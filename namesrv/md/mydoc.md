## 1. 目录结构

namesrv
├─ NamesrvController.java // 执行初始化逻辑，加载配置、注册Processor等
├─ NamesrvStartup.java // NameServer的启动类, 启动netty server
├─ kvconfig
│    ├─ KVConfigManager.java // namespace和config配置管理
│    └─ KVConfigSerializeWrapper.java // 将获取到的配置json序列化
├─ processor
│    ├─ ClusterTestRequestProcessor.java //处理请求类型。
│    └─ DefaultRequestProcessor.java  // 默认地请求处理器, 处理数据包
└─ routeinfo
├─ BrokerHousekeepingService.java // netty 的channel共用方法抽象
└─ RouteInfoManager.java   // 路由管理器，维护topic, broker, clusterName, brokerAddr等信息



## 2. 路由注册

### broker 向 NameServer 发送心跳

在 BrokerController#start 方法里，broker 通过 BrokerController#registerBrokerAll 方法向 NameServer 发送心跳包，其中使用定时任务线程池定时发送，每隔30s 发送一次， brokerConfig#getRegisterNameServerPeriod 的默认值为30s。

### NameServer 处理心跳包

NameServer接收到broker发送过来的请求后，首先会在DefaultRequestProcessor 网络处理器解析请求类型，请求类型如果为 RequestCode.REGISTER_BROKER, 则最终的请求会到RouteInfoManager里的registerBroker方法。

