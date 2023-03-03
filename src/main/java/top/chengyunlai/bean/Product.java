package top.chengyunlai.bean;

/**
 * @ClassName
 * @Description
 * Redis 最常被用作缓存，那如果我要用 Redis 缓存一个 Java 对象，应该怎么做呢？
 * 我们常用的方式是把 Java 对象序列化，然后作为字符串存储到 Redis 里面。
 * @Author:chengyunlai
 * @Date
 * @Version 1.0
 **/
public class Product {
    private String name;

    private double price;

    private String desc;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Product{");
        sb.append("name='").append(name).append('\'');
        sb.append(", price=").append(price);
        sb.append(", desc='").append(desc).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
