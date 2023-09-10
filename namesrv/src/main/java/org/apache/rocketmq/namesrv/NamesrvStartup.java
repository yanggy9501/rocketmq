/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.namesrv;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.apache.rocketmq.srvutil.ShutdownHookThread;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * NameServer 启动类
 * NameServer 担当着路由注册、发现、动态地维护broker相关信息的角色, NameServer 不提供Master-slave同步机制，但是能够保证数据的最终一致性。
 * 1. 动态路由发现和注册功能，broker 启动时，会将 brokerAddr 注册到 NameServer 里；路由发现是指客户端会定时的向 NameServer根据 topic 拉取路由的最新信息
 * 2. 动态剔除功能，每隔 10 s NameServer 会自动扫描所有的 broker, 如果有 broker失效，那么会从地址列表里将其剔除掉。
 *
 * PS: 需要配置环境变量 ROCKETMQ_HOME=ROCKETMQ的主目录（随意:配置环境变量(值为RocketMQ的安装路径)）
 * 在启动参数配置环境变化，或者在系统中配置环境变量，如
 * ROCKETMQ_HOME=\rocketmq-all-4.9.1\rocketmq-all-4.9.1-source-release
 */
public class NamesrvStartup {

    private static InternalLogger log;
    private static Properties properties = null;
    private static CommandLine commandLine = null;

    public static void main(String[] args) {
        main0(args);
    }

    public static NamesrvController main0(String[] args) {
        //1. NamesrvController 为 NameServer 的核心组件，类似于Web应用中的 Controller，负责接收处理网络请求。
        try {
            // 创建 NameSrvController，NameSrv 的配置存放在 user.home\namesrv\ 目录下
            NamesrvController controller = createNamesrvController(args);
            // 启动 Namesrv
            start(controller);
            String tip = "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
            log.info(tip);
            System.out.printf("%s%n", tip);
            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    /*xxx:创建NameSrvController, 加载 namesrvConfig 和 nettyServerConfig*/
    public static NamesrvController createNamesrvController(String[] args) throws IOException, JoranException {
        // 设置系统环境变量-当前框架版本
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        // PackageConflictDetect#detectFastjson
        // 检测命令行参数
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        commandLine = ServerUtil.parseCmdLine("mqnamesrv", args, buildCommandlineOptions(options), new PosixParser());
        if (null == commandLine) {
            System.exit(-1);
            return null;
        }
        // NameServer 的三个核心配置 NamesrvConfig
        final NamesrvConfig namesrvConfig = new NamesrvConfig();
        // Netty 网络通信配置 NettyServerConfig
        final NettyServerConfig nettyServerConfig = new NettyServerConfig();
        // Netty 默认端口 9876
        nettyServerConfig.setListenPort(9876);
        // -c 和 -p 参数解析，-c config 通过启动命令指定配置以定制化的额外配置 NamesrvConfig、NettyServerConfig
        if (commandLine.hasOption('c')) {
            String file = commandLine.getOptionValue('c');
            if (file != null) {
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                properties = new Properties();
                properties.load(in);
                MixAll.properties2Object(properties, namesrvConfig);
                MixAll.properties2Object(properties, nettyServerConfig);

                namesrvConfig.setConfigStorePath(file);

                System.out.printf("load config properties file OK, %s%n", file);
                in.close();
            }
        }
        // -p print 参数打印配置日志，打印由 logback_namesrv.xml 配置
        if (commandLine.hasOption('p')) {
            InternalLogger console = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_CONSOLE_NAME);
            MixAll.printObjectProperties(console, namesrvConfig);
            MixAll.printObjectProperties(console, nettyServerConfig);
            System.exit(0);
        }

        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);
        // ROCKETMQ_HOME 环境变量检测
        if (null == namesrvConfig.getRocketmqHome()) {
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation%n", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }
        // 日志配置
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");

        log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);

        MixAll.printObjectProperties(log, namesrvConfig);
        MixAll.printObjectProperties(log, nettyServerConfig);

        final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig);

        // remember all configs to prevent discard
        controller.getConfiguration().registerConfig(properties);

        return controller;
    }

    public static NamesrvController start(final NamesrvController controller) throws Exception {

        if (null == controller) {
            throw new IllegalArgumentException("NamesrvController is null");
        }
        // start 之间需要做一些准备工作：比如加载配置、创建Netty Server实例、注册请求处理器、扫描所有的失联的broker等
        boolean initResult = controller.initialize();
        if (!initResult) {
            controller.shutdown();
            System.exit(-3);
        }
        // 服务关闭钩子，在服务正常关闭时执行。
        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                controller.shutdown();
                return null;
            }
        }));
        // 启动服务，启动 netty
        controller.start();

        return controller;
    }

    public static void shutdown(final NamesrvController controller) {
        controller.shutdown();
    }

    public static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "Name server config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    public static Properties getProperties() {
        return properties;
    }
}
