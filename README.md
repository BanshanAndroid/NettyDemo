# NettyDemo

## TCP Client

#### 初始化
```
ConnectionClient.getConnectionClient().init()
ConnectionClient.getConnectionClient().connect()
```
#### 发送消息

```
ConnectionClient.getConnectionClient().writeMessage()
```

#### 断开连接
```
ConnectionClient.getConnectionClient().disConnect()
```

## TCP Server

#### 启动服务

```
ConnectionServer.getEngin().init()
```

#### 向某个客户端发送消息

```
ConnectionServer.getEngin().writeMessage()
```

#### 关闭服务

```
ConnectionServer.getEngin().disConnect()
```



