package top.chengyunlai.bean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ProductTest {
    private static RedisClient redisClient;

    private static StatefulRedisConnection<String, String> connection;

    private static RedisAsyncCommands<String, String> asyncCommands;

    private static Properties properties;
    static InputStream input = null;

    private static String redisURL = "";

    static {
        try {
            properties = new Properties();
            input = ProductTest.class.getClassLoader().getResourceAsStream("config.properties");
            // 加载properties文件
            properties.load(input);
            // 获取属性值
            redisURL = properties.getProperty("RedisURL");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void before(){
        redisClient = RedisClient.create(redisURL+"0");
        connection = redisClient.connect();
        asyncCommands = connection.async();
    }

    @After
    public void after(){
        connection.close();
        redisClient.shutdown();
    }

    /**
     * @Description: 操控Redis客户端，往服务器中存放一个对象的JSON串
     * @Param: []
     * @return: void
     * @Author: chengyunlai
     * @Date: 2023/3/1
     */
    @Test
    public void testCacheProduct() throws Exception {
        // 使用 Jackson 的 ObjectMapper 类创建一个对象，用于将 Java 对象转换为 JSON 字符串。在此示例中，创建了一个 Product 对象，并设置了其名称、价格和描述属性。
        ObjectMapper objectMapper = new ObjectMapper();

        Product product = new Product(); // 创建Product对象
        product.setName("杯子");
        product.setPrice(100d);
        product.setDesc("这是一个杯子");
        String json = objectMapper.writeValueAsString(product);

        // 创建 Redis 连接
        // /0 表示 Redis 数据库的编号，它是 Redis 提供的一种多数据库方案。默认情况下，Redis 会创建 16 个数据库，分别编号为 0~15，其中编号为 0 的数据库是默认数据库。
        // RedisClient client = RedisClient.create(redisURL+"/0");

        // StatefulRedisConnection<String, String> connection = client.connect();
        // 连接完之后，就可以通过 sync() 方法创建一个同步的 Command 对象，这个对象就是用来执行 Redis 命令的关键对象。
        // RedisCommands<String, String> syncCommands = connection.sync();

        // 执行 Redis 命令
        // String value = syncCommands.get("name");

        // 输出结果
        // System.out.println(value);

        // StatefulRedisConnection<String, String> connection = client.connect();
        // RedisAsyncCommands<String, String> asyncCommands = connection.async();

        // 需要注意的是，由于 Redis 是内存数据库，因此不建议在 Redis 中存储大量的 JSON 数据。
        // 在某些情况下，可以考虑使用 Redis 作为缓存，而不是持久化存储 JSON 数据。另外，如果要存储复杂的数据结构，建议使用 Redis 的数据结构类型，例如哈希表、列表等，而不是简单的键值对。
        asyncCommands.set("product", json).get(1, TimeUnit.SECONDS);

    }

    /**
     * @Description: 操控Redis客户端，从服务器读取一个JSON字符串，并反序列化
     * @Param: []
     * @return: void
     * @Author: chengyunlai
     * @Date: 2023/3/1
     */
    @Test
    public void testGetProduct() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        // 拿到JSON字符串
        String json = asyncCommands.get("product").get(1, TimeUnit.SECONDS);
        // 通过 readValue反序列化
        Product product = objectMapper.readValue(json, new TypeReference<Product>() {
        });
        System.out.println(product);
    }

    /**
     * @Description: 利用Redis字符串实现分布式锁
     * @Param: []
     * @return: void
     * @Author: chengyunlai
     * @Date: 2023/3/1
     */
    @Test
    public void testLock() throws Exception {
        int threadNum = 1;
        CountDownLatch countDownLatch = new CountDownLatch(threadNum);

        Runnable runnable = () -> {
            try {
                countDownLatch.await();
                while (true) {
                    // 获取锁
                    // 设置ex参数和nx参数
                    SetArgs setArgs = SetArgs.Builder.ex(5).nx();
                    String succ = asyncCommands.set("update-product",
                            Thread.currentThread().getName(), setArgs).get(1, TimeUnit.SECONDS);
                    // 加锁失败
                    if (!"OK".equals(succ)) {
                        System.out.println(Thread.currentThread().getName() + "加锁失败，自选等待锁");
                        Thread.sleep(100);
                    } else {
                        System.out.println(Thread.currentThread().getName() + "加锁成功");
                        break;
                    }
                }
                // 加锁成功
                System.out.println(Thread.currentThread().getName() + "开始执行业务逻辑");
                Thread.sleep(1000);
                System.out.println(Thread.currentThread().getName() + "完成业务逻辑");
                // 释放锁
                asyncCommands.del("update-product").get(1, TimeUnit.SECONDS);
                System.out.println(Thread.currentThread().getName() + "释放锁");
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        Thread thread1 = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        Thread thread3 = new Thread(runnable);
        thread1.start();
        thread2.start();
        thread3.start();
        countDownLatch.countDown();

        Thread.sleep(TimeUnit.DAYS.toMillis(1));
    }
    /**
     * @Description: 限流
     *
     * 有些高并发的场景，例如抢购、秒杀，流量峰值会比较高，后端业务资源是有限的，需要对业务进行限流才可以支持业务的正常运转。比如说，我们要求整个服务的 QPS 不能超过 1000，这个时候就可以用 Redis 的 INCR 命令，来实现限流的效果。
     *
     * @Param: []
     * @return: void
     * @Author: chengyunlai
     * @Date: 2023/3/1
     */
    @Test
    public void testLimit() throws Exception {
        String prefix = "order-service";
        long maxQps = 10;
        long nowSeconds = System.currentTimeMillis() / 1000;
        for (int i = 0; i < 15; i++) {
            Long result = asyncCommands.incr(prefix + nowSeconds).get(1, TimeUnit.SECONDS);
            if (result > maxQps) {
                System.out.println("请求被限流"+ nowSeconds);
            }else{
                System.out.println("请求正常被处理"+nowSeconds);
            }
        }
    }
}