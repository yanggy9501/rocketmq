# Store 模块

## Store 目录

 store模块是rocketmq存储消息和管理消息的模块，也是rocketmq的核心模块之一。

**store 目录**

|

|-- commitLog: 消息存储目录, 文件名当前文件所在的全局偏移量，注意全局偏移量不一定从00000000000000000000开始。大小是一样的! 其实默认是1GB， 满了会自动会生成下一个文件。

|--config： topic相关信息、消费者过滤器、消费位移等信息。

|--consumequeue:  消息消费队列目录

|--index: index下目录下的文件是专门为消息订阅设计的索引文件。

|-- abort:  abort文件如果存在说明Broker非正常关闭，因为在broker正常退出的情况下，该abort文件会被删除掉， abort文件在启动时创建，正常退出之前删除。

|-- checkPoint: 保存consumeQueue、index、commitlog最后一次刷盘时间。

****

### commitLog 目录

commitLog 目录为 消息存储目录, 文件名当前文件所在的全局偏移量，全局偏移量不一定从00000000000000000000开始。每个文件的大小一样，默认是1GB， 满了会自动会生成下一个文件。

### config 目录

存放并备份 topic相关信息、消费者过滤器、消费位移等信息。

config目录包含了消费位点、延迟消费位点、topic相关信息的配置等。

>consumerFilter: 主题消息过滤信息。
>
>consumerOffset: 集群消息模式消息消费进度。
>
>delayOffset: 延时消息队列拉取进度。
>
>subscriptionGroup: 消息消费组的配置消息。
>
>topic: topic 配置信息。



### consumequeue 目录

 消息消费队列目录。consumequeue目录包含了我们自定义的topic名称，topic 名称为目录名，在该 topic 目录下的子目录为该 topic 的队列ID目录。

真正存储消息的文件是commitlog里的文件，为了加快检索速度, 无后缀的20位长度命名的文件00000000000000000000是CommitLog关于消息消费的索引文件，如果观察仔细，文件的命名也是有规律的，每个文件的名字大小偏差为30万*20, 也就是6000000, 该值为全局偏移量。文件里的消息条目记录消息消费位点、消息大小和tag的hashcode等， 该文件里的条目存储格式如下:

>   |--8字节 commitlog offset --| -- 4 字节 size --|-- 8 字节 tag hashcode --|

单个00000000000000000000文件包含30万个条目，每个条目的长度为20个字节，因此单个文件的长度为30万* 20 约等于5860KB。

### index 目录

dex下目录下的文件是专门为消息订阅设计的索引文件。 通过索引加快检索速度。

### abort 文件

bort文件如果存在说明Broker非正常关闭，因为在broker正常退出的情况下，该abort文件会被删除掉， abort文件在启动时创建，正常退出之前删除。

### checkPoint 文件

保存consumeQueue、index、commitlog最后一次刷盘时间。



## store 工程目录解析

```java
store
├─ AllocateMappedFileService.java
├─ AppendMessageCallback.java
├─ AppendMessageResult.java
├─ AppendMessageStatus.java
├─ CommitLog.java
├─ CommitLogDispatcher.java
├─ ConsumeQueue.java
├─ ConsumeQueueExt.java
├─ DefaultMessageFilter.java
├─ DefaultMessageStore.java   // 消息存储的核心类，也是消息存储的入口
├─ DispatchRequest.java
├─ GetMessageResult.java
├─ GetMessageStatus.java
├─ MappedFile.java
├─ MappedFileQueue.java
├─ MessageArrivingListener.java
├─ MessageExtBrokerInner.java
├─ MessageFilter.java
├─ MessageStore.java
├─ PutMessageLock.java
├─ PutMessageReentrantLock.java
├─ PutMessageResult.java
├─ PutMessageSpinLock.java
├─ PutMessageStatus.java
├─ QueryMessageResult.java
├─ ReferenceResource.java
├─ RunningFlags.java
├─ SelectMappedBufferResult.java
├─ StoreCheckpoint.java
├─ StoreStatsService.java
├─ StoreUtil.java
├─ TransientStorePool.java
├─ config   // 消息配置
│    ├─ BrokerRole.java
│    ├─ FlushDiskType.java
│    ├─ MessageStoreConfig.java // 消息核心配置，含消息存储路径、commitLog 路径、commitFile大小(默认1G)、每个队列大小(默认300000*20, 约等于5860KB)
│    └─ StorePathConfigHelper.java
 // 阿里 dleder中间件 
├─ dledger
│    └─ DLedgerCommitLog.java  // 消息提交管理
├─ ha
│    ├─ HAConnection.java
│    ├─ HAService.java
│    └─ WaitNotifyObject.java
├─ index  //  索引管理
│    ├─ IndexFile.java    // 索引文件管理
│    ├─ IndexHeader.java   // 索引头实体类
│    ├─ IndexService.java   // 索引服务
│    └─ QueryOffsetResult.java
├─ schedule   //   定时任务，管理延迟消费，实际上是延迟存储
│    ├─ DelayOffsetSerializeWrapper.java
│    └─ ScheduleMessageService.java
├─ stats // broker 状态管理
│    ├─ BrokerStats.java
│    └─ BrokerStatsManager.java
└─ util
       └─ LibC.java
```

