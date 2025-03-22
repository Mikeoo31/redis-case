## 自研分布式锁
### 锁的种类
- JVM锁：synchronized关键字，通过对象锁实现，锁住整个对象，Lock接口实现，锁住一段代码块。但是在分布式环境下，JVM锁就不再起作用了，因为JVM是`单机环境`，无法实现分布式锁。如果在单机环境下使用JVM锁，那么就会出现少买多卖的问题。
- 分布式锁：用于在`分布式系统`中控制多个进程或线程对共享资源访问的机制。

### 基于Redis的分布式锁
#### v1.0版本
- 基于Redis的分布式锁，使用setnx命令实现，并用递归的方式来实现锁的获取；当业务结束后自动删除锁。
```java
    Boolean setResult = redisTemplate.opsForValue().setIfAbsent(lockKey, value);
    if (!setResult){// 等待20ms递归调用
        try{TimeUnit.MILLISECONDS.sleep(20);}catch(InterruptedException e){e.printStackTrace();}
        getInventory();// 递归调用
    }
```
```java
    try{
        ...// 业务代码
    }finally {
        redisTemplate.delete(lockKey); // 删除锁
    }
```
- 此版本的实现方式，存在以下问题：
    - 在高并发场景下，会出现线程阻塞，导致系统响应变慢。并且可能会出息那stackoverflow的异常。
    - 并且高并发场景下，禁止使用递归调用。

#### v2.0版本
- 在v1.0版本的基础上，将递归调用改为自旋的方式，避免stackoverflow的异常。
```java
    // 改用自旋锁
    while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value)) {
        try{TimeUnit.MILLISECONDS.sleep(20);}catch(InterruptedException e){e.printStackTrace();}      
    }
```
- 此版本的实现方式，存在以下问题：
    - 高并发场景下，会出现线程阻塞，导致系统响应变慢。
    - 某时刻系统挂了，则会导致锁一直被占用，导致其他线程无法正常执行。

#### v3.0版本
- 在v2.0版本的基础上，增加了超时时间，避免线程一直等待，造成系统资源浪费。
- **注意**：设置超时时间的原子性，避免线程在设置超时时间时，其他线程抢占锁。
```java
    // 改用自旋锁，并设置超时时间为10s，同时保证设置超时时间的原子性
    while (!redisTemplate.opsForValue().setIfAbsent(lockKey, value, 10, TimeUnit.SECONDS)) {
        try{TimeUnit.MILLISECONDS.sleep(20);}catch(InterruptedException e){e.printStackTrace();}      
    }
```
- 此版本的实现方式，存在以下问题：
    - 超时时间设置不合理，导致锁一直被占用，导致其他线程无法正常执行。
    - 当业务执行时间超过10s时，可能会导致误删其他线程的锁。

#### v4.0版本
- 在v3.0版本的基础上，在删除锁时，增加了判断锁是否存在的逻辑，避免误删其他线程的锁。
```java
finally {
      // 改进：为了防止某个线程将其他线程的锁删掉，在删除之前，必须先判断是否是自己的锁
      if (redisTemplate.opsForValue().get(lockKey).equals(value)) {
         redisTemplate.delete(lockKey);
      }
}
```
- 此版本的实现方式，存在以下问题：
    - 增加了判断锁是否存在的逻辑，但没有保证原子性，可能导致线程在判断锁是否存在时，其他线程抢占锁。

#### v5.0版本
- 在v4.0版本的基础上，增加了原子性保证，使用lua脚本来实现删除锁的原子性。
```java
// 原子性保证
finally {
     // 改进：保证删除锁的原子性
     String evalStr = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 " +
                    "end";
     redisTemplate.execute(new DefaultRedisScript<>(evalStr, Boolean.class), Arrays.asList(lockKey), value);
}
```
- 此版本的实现方式，解决了原子性问题，并且保证了删除锁的原子性。
- 到这为止，基于Redis的分布式锁的实现基本完成。一般情况下，基于Redis的分布式锁的性能已经足够，在不是超高并发场景下，这样的分布式锁足以满足当前的业务逻辑。
- 但是，还是存在着问题：没有考虑锁的**可重入性**，即同一个线程可以多次获取同一个锁，并且不会发生死锁。
- 如果需要真正自研一把锁，那么就必须满足JUC的Lock规范之一：可重入性。

**可重入锁：** 可重入锁是指在同一个线程中可以对同一个锁进行多次加锁，不会发生死锁。

#### v6.0版本
- 利用redis的hset命令，实现可重入锁。当某个方法需要多次获取同一个锁时，只需要在第一次获取锁时，设置一个超时时间，并设置一个**标识**，当第二次获取锁时，判断标识是否存在，如果存在，则将其标识+1，释放锁时，从里到外，将标识-1，直到标识为0，才释放锁。

```java 
// 加锁
String script =
     "if redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1  then " +
        "redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
        "redis.call('expire', KEYS[1], ARGV[2]) " +
        "return 1 " +
     "else " +
        "return 0 " +
     "end";
            
     while(!stringRedisTemplate.execute(new DefaultRedisScript<>(script, 
             Boolean.class), Arrays.asList(lockName), threadUUID, String.valueOf(expireTime))){
        try{TimeUnit.MILLISECONDS.sleep(20);}catch(InterruptedException e){e.printStackTrace();}
     }
     
// 释放锁
String script =
      "local exists = redis.call('hexists', KEYS[1], ARGV[1])\n" +
            "if exists == 0 then\n" +
                "return 0\n" +
            "else\n" +
                "local count = redis.call('hincrby', KEYS[1], ARGV[1], -1)\n" +
                "if count == 0 then\n" +
                    "redis.call('del', KEYS[1])\n" +
                    "return 1\n" +
                "else\n" +
                    "return 2\n" +
                "end\n" +
           "end";

        Long flag = stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Arrays.asList(lockName),
                threadUUID
        );
```
- 使用hset命令并配合lua脚本，实现了可重入锁。

#### v7.0版本
- 在v6.0版本的基础上，添加定时任务，实现锁的自动续期。（看门狗）
- 定时任务的实现，可以使用redis的expire命令，设置一个定时任务，每隔一段时间，对锁进行续期。
```java
// 定时任务
private void resetExpireTime() {
        String script =
        "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then " +
        "return redis.call('expire', KEYS[1], ARGV[2])" +
        "else " +
        "return 0 " +
        "end";
        // 每隔 expireTime/3 秒执行一次自动续期
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if(stringRedisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), threadUUID, String.valueOf(expireTime))){
                    resetExpireTime();
                }
            }
        }, expireTime * 1000 / 3);
}
```
- 每个30ms，对锁进行一次续期，直到锁过期。

**扩展：** 在v7.0版本的基础上，配合工厂模式，可以实现其他类型的锁，比如基于Zookeeper的可重入锁，基于MySQL的可重入锁等，实现了程序的可扩展性。
```java
public Lock getDistributedLock(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("typeName cannot be null");
        }else if (typeName.equals("redis")) {
            return new DistributedLock(redisTemplate,"redisLock",uuid);
        }else if(typeName.equals("mysql")) {
            //TODO: implement mysql lock
            return null;
        }else if(typeName.equals("zookeeper")){
            //TODO: implement zookeeper lock
            return null;
        }
        return null;
    }
```
- 这样，程序就可以根据需求，选择不同的锁类型，来实现不同的功能。

#### 总结
- 基于redis命令的setnx的分布式锁，在加锁和释放锁的过程中，都需要保证其原子性
- 为了保证其原子性，就必须使用lua脚本
- 自研分布式锁，就必须满足JUC的Lock规范之一：可重入性
- 添加定时任务，实现锁的自动续期，这样可以防止锁的过期，提高系统的稳定性
- 扩展性：可以根据需求，选择不同的锁类型，来实现不同的功能。