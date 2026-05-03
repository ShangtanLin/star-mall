package com.github.shangtanlin;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 查询性能测试（纯 Java，不启动 Spring）
 */
public class EsPerformanceTest {

    private static ElasticsearchClient client;

    private static final String INDEX_NAME = "test_order_index";

    @BeforeAll
    static void setup() throws Exception {
        // 配置 ObjectMapper
        ObjectMapper objectMapper = JsonMapper.builder().build();

        // 忽略证书验证（开发环境）
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial((x509Certificates, s) -> true)
                .build();

        // 用户名 + 密码（根据你的 ElasticsearchConfig）
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "z0Me_63Lyq4GnIZ0g_KW"));

        // 构建 RestClient
        RestClient restClient = RestClient.builder(
                new HttpHost("192.168.244.130", 9200, "https")
        ).setHttpClientConfigCallback(http ->
                http.setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(provider)
                        .setSSLHostnameVerifier((hostname, session) -> true)
        ).build();

        // 创建 ElasticsearchClient
        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(objectMapper)
        );
        client = new ElasticsearchClient(transport);

        System.out.println("ES 连接成功！");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (client != null) {
            client._transport().close();
        }
    }

    /**
     * 步骤1：创建索引
     */
    @Test
    void createIndex() throws Exception {
        // 先删除已存在的索引（如果有）
        try {
            client.indices().delete(d -> d.index(INDEX_NAME));
            System.out.println("已删除旧索引");
        } catch (Exception e) {
            // 索引不存在，忽略
        }

        // 创建新索引
        String mappingJson = """
            {
              "mappings": {
                "properties": {
                  "sub_order_id": { "type": "long" },
                  "sub_order_sn": { "type": "keyword" },
                  "user_id": { "type": "long" },
                  "shop_id": { "type": "long" },
                  "status": { "type": "integer" },
                  "is_delete": { "type": "integer" },
                  "pay_amount": { "type": "double" },
                  "create_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
                  "items": {
                    "type": "nested",
                    "properties": {
                      "spu_id": { "type": "long" },
                      "sku_id": { "type": "long" },
                      "spu_name": { "type": "text", "analyzer": "standard" },
                      "sku_name": { "type": "text" },
                      "price": { "type": "double" },
                      "quantity": { "type": "integer" }
                    }
                  }
                }
              }
            }
            """;

        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(mappingJson))
        );

        client.indices().create(request);
        System.out.println("索引创建成功：" + INDEX_NAME);
    }

    /**
     * 步骤2：批量插入20万订单数据（先删除旧数据）
     */
    @Test
    void bulkInsertOrders() throws Exception {
        System.out.println("\n========================================");
        System.out.println("开始批量插入数据到 ES...");
        System.out.println("========================================\n");

        // 1. 删除旧索引（如果存在）
        try {
            client.indices().delete(d -> d.index(INDEX_NAME));
            System.out.println("已删除旧索引: " + INDEX_NAME);
        } catch (Exception e) {
            System.out.println("索引不存在，跳过删除");
        }

        // 2. 重新创建索引
        String mappingJson = """
            {
              "mappings": {
                "properties": {
                  "sub_order_id": { "type": "long" },
                  "sub_order_sn": { "type": "keyword" },
                  "user_id": { "type": "long" },
                  "shop_id": { "type": "long" },
                  "status": { "type": "integer" },
                  "is_delete": { "type": "integer" },
                  "pay_amount": { "type": "double" },
                  "create_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
                  "items": {
                    "type": "nested",
                    "properties": {
                      "spu_id": { "type": "long" },
                      "sku_id": { "type": "long" },
                      "spu_name": { "type": "text", "analyzer": "standard" },
                      "sku_name": { "type": "text" },
                      "price": { "type": "double" },
                      "quantity": { "type": "integer" }
                    }
                  }
                }
              }
            }
            """;

        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(mappingJson))
        );
        client.indices().create(request);
        System.out.println("索引创建成功: " + INDEX_NAME);

        // 3. 批量插入数据（20万条）
        int totalOrders = 200000;
        int batchSize = 1000; // 每批插入1000条
        int batchCount = totalOrders / batchSize;

        for (int batch = 0; batch < batchCount; batch++) {
            List<BulkOperation> operations = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                int orderNum = batch * batchSize + i + 1;

                // 构建订单文档
                Map<String, Object> orderDoc = new HashMap<>();
                orderDoc.put("sub_order_id", orderNum);
                orderDoc.put("sub_order_sn", "2024" + String.format("%06d", orderNum));
                orderDoc.put("user_id", 10000 + (orderNum % 1000)); // 1000个用户
                orderDoc.put("shop_id", 2000 + (orderNum % 10));    // 10个店铺
                orderDoc.put("status", orderNum % 4);
                orderDoc.put("is_delete", 0);
                orderDoc.put("pay_amount", 1000 + Math.random() * 9000);
                orderDoc.put("create_time", "2024-01-01 10:00:00");

                // 构建3个商品（nested数组）
                List<Map<String, Object>> items = new ArrayList<>();
                long baseSpuId = 1000L + orderNum * 3;

                String[] productNames = {"手机", "电脑", "耳机", "平板", "手表"};
                String[] appleProducts = {"iPhone", "MacBook", "AirPods", "iPad", "AppleWatch"};
                String[] accessories = {"配件", "保护壳", "充电器", "数据线", "支架"};

                // 商品1
                Map<String, Object> item1 = new HashMap<>();
                item1.put("spu_id", baseSpuId);
                item1.put("sku_id", baseSpuId);
                item1.put("spu_name", "商品" + baseSpuId + "-" + productNames[orderNum % 5]);
                item1.put("sku_name", "SKU" + baseSpuId);
                item1.put("price", 500 + Math.random() * 4500);
                item1.put("quantity", 1 + orderNum % 3);
                items.add(item1);

                // 商品2（含Apple产品名）
                Map<String, Object> item2 = new HashMap<>();
                item2.put("spu_id", baseSpuId + 1);
                item2.put("sku_id", baseSpuId + 1);
                item2.put("spu_name", "商品" + (baseSpuId + 1) + "-" + appleProducts[orderNum % 5]);
                item2.put("sku_name", "SKU" + (baseSpuId + 1));
                item2.put("price", 500 + Math.random() * 4500);
                item2.put("quantity", 1 + orderNum % 3);
                items.add(item2);

                // 商品3
                Map<String, Object> item3 = new HashMap<>();
                item3.put("spu_id", baseSpuId + 2);
                item3.put("sku_id", baseSpuId + 2);
                item3.put("spu_name", "商品" + (baseSpuId + 2) + "-" + accessories[orderNum % 5]);
                item3.put("sku_name", "SKU" + (baseSpuId + 2));
                item3.put("price", 50 + Math.random() * 500);
                item3.put("quantity", 1 + orderNum % 5);
                items.add(item3);

                orderDoc.put("items", items);

                // 添加到批量操作
                operations.add(BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(String.valueOf(orderNum))
                                .document(orderDoc)
                        )
                ));
            }

            // 执行批量插入
            client.bulk(b -> b.index(INDEX_NAME).operations(operations));

            if ((batch + 1) % 20 == 0) {
                System.out.println("已插入 " + (batch + 1) * batchSize + " 条订单...");
            }
        }

        System.out.println("\n========================================");
        System.out.println("数据插入完成！共 " + totalOrders + " 条订单，" + (totalOrders * 3) + " 条商品");
        System.out.println("========================================");

        // 强制刷新索引，确保数据可搜索
        client.indices().refresh(r -> r.index(INDEX_NAME));
        System.out.println("索引已刷新，数据可搜索");
    }

    /**
     * 为用户 10005 批量插入 1万条订单数据到 ES
     * 与 MySQL 数据保持一致，用于对比测试
     */
    @Test
    void bulkInsertOrdersForUser10005() throws Exception {
        Long targetUserId = 10005L;
        int orderCount = 10000;
        int itemsPerOrder = 3;

        System.out.println("\n========================================");
        System.out.println("ES 批量插入测试数据");
        System.out.println("目标用户ID：" + targetUserId);
        System.out.println("订单数量：" + orderCount);
        System.out.println("每订单商品数：" + itemsPerOrder);
        System.out.println("预计商品总数：" + (orderCount * itemsPerOrder));
        System.out.println("========================================\n");

        // 商品名池（与 MySQL 一致）
        String[] productNames = {
            "iPhone 15 Pro Max 苹果手机",
            "MacBook Pro 16英寸 M3芯片笔记本",
            "Sony WH-1000XM5 无线降噪耳机",
            "Dyson V15 无线吸尘器",
            "Nintendo Switch OLED 游戏主机",
            "Canon EOS R6 专业相机",
            "华为 Mate 60 Pro 智能手机",
            "小米14 Ultra 旗舰手机",
            "iPad Pro 12.9英寸 平板电脑",
            "三星 Galaxy S24 手机",
            "联想 ThinkPad X1 Carbon 笔记本",
            "戴尔 XPS 15 笔记本电脑",
            "索尼 PS5 游戏主机",
            "微软 Xbox Series X 游戏机",
            "AirPods Pro 2 无线耳机"
        };

        // 查询 ES 中 user_id=10005 的现有文档数量
        Query existingQuery = Query.of(q -> q.term(t -> t.field("user_id").value(targetUserId)));
        SearchResponse<Map> existingResponse = client.search(s -> s
                .index(INDEX_NAME)
                .query(existingQuery)
                .size(0),
                Map.class
        );
        long existingCount = existingResponse.hits().total().value();
        System.out.println("ES 中用户 " + targetUserId + " 现有订单数：" + existingCount);

        // 获取现有文档的最大 ID（假设 ID 是订单号数字部分）
        long startOrderId = 200001; // 从 200001 开始，避免与现有数据冲突

        int batchSize = 1000;
        int batchCount = orderCount / batchSize;

        long startTime = System.currentTimeMillis();

        for (int batch = 0; batch < batchCount; batch++) {
            List<BulkOperation> operations = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                int orderNum = (int) (startOrderId + batch * batchSize + i);

                // 构建订单文档
                Map<String, Object> orderDoc = new HashMap<>();
                orderDoc.put("sub_order_id", orderNum);
                orderDoc.put("sub_order_sn", "2024BATCH" + String.format("%06d", batch * batchSize + i + 1));
                orderDoc.put("user_id", targetUserId);
                orderDoc.put("shop_id", 888);
                orderDoc.put("status", 1);
                orderDoc.put("is_delete", 0);
                orderDoc.put("pay_amount", 1000 + (orderNum % 100) * 50);
                orderDoc.put("create_time", "2024-06-01 10:00:00");

                // 构建3个商品（与 MySQL 一致）
                List<Map<String, Object>> items = new ArrayList<>();
                for (int j = 0; j < itemsPerOrder; j++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("spu_id", 10000 + j);
                    item.put("sku_id", 10000 + j);
                    item.put("spu_name", productNames[j % productNames.length]);
                    item.put("sku_name", productNames[j % productNames.length] + " 标准版");
                    item.put("price", 500 + (j + 1) * 200);
                    item.put("quantity", 1);
                    items.add(item);
                }
                orderDoc.put("items", items);

                // 添加到批量操作
                operations.add(BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(String.valueOf(orderNum))
                                .document(orderDoc)
                        )
                ));
            }

            // 执行批量插入
            client.bulk(b -> b.index(INDEX_NAME).operations(operations));

            System.out.println("已插入第 " + ((batch + 1) * batchSize) + " 条订单...");
        }

        // 强制刷新索引
        client.indices().refresh(r -> r.index(INDEX_NAME));

        long endTime = System.currentTimeMillis();
        System.out.println("\n========================================");
        System.out.println("插入完成！");
        System.out.println("订单总数：" + orderCount);
        System.out.println("商品总数：" + (orderCount * itemsPerOrder));
        System.out.println("总耗时：" + (endTime - startTime) + "ms");
        System.out.println("========================================");

        // 验证插入结果
        Query verifyQuery = Query.of(q -> q.term(t -> t.field("user_id").value(targetUserId)));
        SearchResponse<Map> verifyResponse = client.search(s -> s
                .index(INDEX_NAME)
                .query(verifyQuery)
                .size(0),
                Map.class
        );
        long newCount = verifyResponse.hits().total().value();
        System.out.println("验证：ES 中用户 " + targetUserId + " 订单总数：" + newCount);
    }

    /**
     * 真实业务场景：用户查询订单列表第一页
     * 参数：keyword="iPhone", userId=10005, pageNo=1, pageSize=10
     */
    @Test
    void testNestedQueryRealBusiness() throws Exception {
        String keyword = "iPhone";
        Long userId = 10005L;
        int pageNo = 1;
        int pageSize = 10;

        System.out.println("\n========================================");
        System.out.println("ES 真实业务场景测试（用户查看订单列表第一页）");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("用户订单数：约10208条");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("页码：" + pageNo);
        System.out.println("每页条数：" + pageSize);
        System.out.println("========================================\n");

        // 构建查询条件
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("user_id").value(userId)));
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        // 计算分页参数
        int from = (pageNo - 1) * pageSize;  // 第一页：from = 0

        // 构建搜索请求
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .from(from)
                .size(pageSize)
                .sort(so -> so.field(f -> f.field("create_time").order(SortOrder.Desc)))
        );

        System.out.println("ES 分页参数：from=" + from + ", size=" + pageSize);
        System.out.println("（只查询需要的10条数据，不是全部10000条）");
        System.out.println();

        // 执行10次取平均值
        long totalQueryTime = 0;   // 纯查询用时
        long totalPackageTime = 0; // 封装用时
        int totalHits = 0;

        for (int i = 1; i <= 10; i++) {
            // ① ES 查询阶段
            long queryStart = System.currentTimeMillis();
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            long queryEnd = System.currentTimeMillis();
            long queryTime = queryEnd - queryStart;

            totalHits = (int) response.hits().total().value();

            // ② 数据封装阶段
            long packageStart = System.currentTimeMillis();
            List<Map<String, Object>> results = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                Map<String, Object> doc = hit.source();
                if (doc != null) {
                    Map<String, Object> orderVO = new HashMap<>();
                    orderVO.put("sub_order_id", doc.get("sub_order_id"));
                    orderVO.put("sub_order_sn", doc.get("sub_order_sn"));
                    orderVO.put("user_id", doc.get("user_id"));
                    orderVO.put("status", doc.get("status"));
                    orderVO.put("pay_amount", doc.get("pay_amount"));
                    orderVO.put("create_time", doc.get("create_time"));
                    orderVO.put("items", doc.get("items"));
                    results.add(orderVO);
                }
            }
            long packageEnd = System.currentTimeMillis();
            long packageTime = packageEnd - packageStart;

            totalQueryTime += queryTime;
            totalPackageTime += packageTime;

            System.out.println("第 " + i + " 次查询: 查询用时 " + queryTime + "ms, " +
                    "封装用时 " + packageTime + "ms, 总用时 " + (queryTime + packageTime) + "ms, " +
                    "总命中 " + totalHits + " 条, 返回 " + results.size() + " 条");
        }

        System.out.println("\n========================================");
        System.out.println("平均查询用时: " + (totalQueryTime / 10) + "ms");
        System.out.println("平均封装用时: " + (totalPackageTime / 10) + "ms");
        System.out.println("平均总用时: " + ((totalQueryTime + totalPackageTime) / 10) + "ms");
        System.out.println("总命中数: " + totalHits + " 条");
        System.out.println();
        System.out.println("性能分析：");
        System.out.println("  查询用时：ES 内部倒排索引定位 + 返回文档");
        System.out.println("  封装用时：遍历 hits + 创建 VO 对象 + 复制字段");
        System.out.println("  ES 只返回 " + pageSize + " 条数据，封装开销很小");
        System.out.println("========================================");
    }

    /**
     * 真实业务场景：商家查询店铺订单列表第一页
     * 参数：keyword="iPhone", shopId=888, pageNo=1, pageSize=10
     */
    @Test
    void testShopOrderQueryRealBusiness() throws Exception {
        String keyword = "iPhone";
        Long shopId = 888L;
        int pageNo = 1;
        int pageSize = 10;

        System.out.println("\n========================================");
        System.out.println("ES 商家查询店铺订单测试（商家查看订单列表第一页）");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("店铺ID：" + shopId);
        System.out.println("页码：" + pageNo);
        System.out.println("每页条数：" + pageSize);
        System.out.println("========================================\n");

        // 构建查询条件
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("shop_id").value(shopId)));
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        // 计算分页参数
        int from = (pageNo - 1) * pageSize;

        // 构建搜索请求
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .from(from)
                .size(pageSize)
                .sort(so -> so.field(f -> f.field("create_time").order(SortOrder.Desc)))
        );

        System.out.println("ES 分页参数：from=" + from + ", size=" + pageSize);
        System.out.println("查询条件：商品名包含 '" + keyword + "' + 店铺ID=" + shopId);
        System.out.println();

        // 执行10次取平均值
        long totalQueryTime = 0;
        long totalPackageTime = 0;
        int totalHits = 0;

        for (int i = 1; i <= 10; i++) {
            // ① ES 查询阶段
            long queryStart = System.currentTimeMillis();
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            long queryEnd = System.currentTimeMillis();
            long queryTime = queryEnd - queryStart;

            totalHits = (int) response.hits().total().value();

            // ② 数据封装阶段
            long packageStart = System.currentTimeMillis();
            List<Map<String, Object>> results = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                Map<String, Object> doc = hit.source();
                if (doc != null) {
                    Map<String, Object> orderVO = new HashMap<>();
                    orderVO.put("sub_order_id", doc.get("sub_order_id"));
                    orderVO.put("sub_order_sn", doc.get("sub_order_sn"));
                    orderVO.put("user_id", doc.get("user_id"));
                    orderVO.put("shop_id", doc.get("shop_id"));
                    orderVO.put("status", doc.get("status"));
                    orderVO.put("pay_amount", doc.get("pay_amount"));
                    orderVO.put("create_time", doc.get("create_time"));
                    orderVO.put("items", doc.get("items"));
                    results.add(orderVO);
                }
            }
            long packageEnd = System.currentTimeMillis();
            long packageTime = packageEnd - packageStart;

            totalQueryTime += queryTime;
            totalPackageTime += packageTime;

            System.out.println("第 " + i + " 次查询: 查询用时 " + queryTime + "ms, " +
                    "封装用时 " + packageTime + "ms, 总用时 " + (queryTime + packageTime) + "ms, " +
                    "总命中 " + totalHits + " 条, 返回 " + results.size() + " 条");
        }

        System.out.println("\n========================================");
        System.out.println("平均查询用时: " + (totalQueryTime / 10) + "ms");
        System.out.println("平均封装用时: " + (totalPackageTime / 10) + "ms");
        System.out.println("平均总用时: " + ((totalQueryTime + totalPackageTime) / 10) + "ms");
        System.out.println("总命中数: " + totalHits + " 条");
        System.out.println();
        System.out.println("对比 MySQL 商家查询：");
        System.out.println("  MySQL：需要先查店铺订单 → 再全文索引 → 再查商品列表（3次查询）");
        System.out.println("  ES：一次查询完成，文档自带商品列表");
        System.out.println();
        System.out.println("对比用户查询：");
        System.out.println("  用户查询：WHERE user_id = xxx（某用户的订单）");
        System.out.println("  商家查询：WHERE shop_id = xxx（某店铺的订单）");
        System.out.println("========================================");
    }

    /**
     * 步骤3：性能测试 - Nested 查询 + 真正接收并封装数据
     * 模拟生产环境：获取 hits 并遍历封装成 VO
     */
    @Test
    void testNestedQueryPerformance() throws Exception {
        String keyword = "iPhone";
        Long userId = 10005L;
        int pageSize = 100; // 每页返回100条（模拟实际分页场景）

        System.out.println("\n========================================");
        System.out.println("ES Nested 查询 + 真正接收数据性能测试");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("用户订单数：约10208条");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("返回数量：" + pageSize + " 条（分页场景）");
        System.out.println("========================================\n");

        // 构建查询条件
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("user_id").value(userId)));
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        // 构建搜索请求
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .size(pageSize)
                .sort(so -> so.field(f -> f.field("create_time").order(SortOrder.Desc)))
        );

        System.out.println("查询条件：商品名包含 '" + keyword + "' + 用户ID=" + userId);
        System.out.println("模拟：真正遍历 hits 并封装数据（订单 + 商品列表）");
        System.out.println();

        // 执行10次取平均值
        long totalTime = 0;

        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();

            SearchResponse<Map> response = client.search(searchRequest, Map.class);

            // 真正遍历并封装数据（模拟生产环境）
            List<Map<String, Object>> results = new ArrayList<>();
            int totalHits = (int) response.hits().total().value();

            for (var hit : response.hits().hits()) {
                Map<String, Object> doc = hit.source();
                if (doc != null) {
                    // 封装订单信息
                    Map<String, Object> orderVO = new HashMap<>();
                    orderVO.put("sub_order_id", doc.get("sub_order_id"));
                    orderVO.put("sub_order_sn", doc.get("sub_order_sn"));
                    orderVO.put("user_id", doc.get("user_id"));
                    orderVO.put("shop_id", doc.get("shop_id"));
                    orderVO.put("status", doc.get("status"));
                    orderVO.put("pay_amount", doc.get("pay_amount"));
                    orderVO.put("create_time", doc.get("create_time"));

                    // 封装商品列表（关键：ES 文档自带商品数据，无需二次查询）
                    List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
                    if (items != null) {
                        List<Map<String, Object>> itemVOs = new ArrayList<>();
                        for (Map<String, Object> item : items) {
                            Map<String, Object> itemVO = new HashMap<>();
                            itemVO.put("spu_id", item.get("spu_id"));
                            itemVO.put("sku_id", item.get("sku_id"));
                            itemVO.put("spu_name", item.get("spu_name"));
                            itemVO.put("sku_name", item.get("sku_name"));
                            itemVO.put("price", item.get("price"));
                            itemVO.put("quantity", item.get("quantity"));
                            itemVOs.add(itemVO);
                        }
                        orderVO.put("items", itemVOs);
                    }
                    results.add(orderVO);
                }
            }

            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;

            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, " +
                    "总命中 " + totalHits + " 条, 实际返回封装 " + results.size() + " 条（含商品列表）");
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("(ES 文档自带商品数据，无需二次查询商品表)");
        System.out.println("对比 MySQL：需要先查订单 → 再查商品 → 应用层组装");
        System.out.println("========================================");
    }

    /**
     * 步骤3.1：性能测试 - 获取全部匹配数据（模拟导出场景）
     * 当用户需要所有数据时，ES 需要返回全部文档
     */
    @Test
    void testNestedQueryGetAllData() throws Exception {
        String keyword = "iPhone";
        Long userId = 10005L;

        System.out.println("\n========================================");
        System.out.println("ES Nested 查询 - 获取全部数据（模拟导出）");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("用户订单数：约10208条");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("========================================\n");

        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("user_id").value(userId)));
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        System.out.println("查询条件：商品名包含 '" + keyword + "' + 用户ID=" + userId);
        System.out.println("模拟：获取并封装全部匹配数据（含商品列表）");
        System.out.println();

        long totalTime = 0;

        for (int i = 1; i <= 3; i++) { // 只测3次，数据量大
            long start = System.currentTimeMillis();

            // 先查一次获取总数
            SearchRequest countRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(query)
                    .size(0)
            );
            SearchResponse<Map> countResponse = client.search(countRequest, Map.class);
            int totalHits = (int) countResponse.hits().total().value();

            // 分批获取全部数据（ES 默认最多返回10000条）
            List<Map<String, Object>> allResults = new ArrayList<>();
            int batchSize = 1000;
            int currentFrom = 0;

            while (currentFrom < totalHits && currentFrom < 10000) { // ES 限制 from+size <= 10000
                final int from = currentFrom; // 创建 final 变量供 lambda 使用

                SearchRequest batchRequest = SearchRequest.of(s -> s
                        .index(INDEX_NAME)
                        .query(query)
                        .from(from)
                        .size(batchSize)
                );

                SearchResponse<Map> batchResponse = client.search(batchRequest, Map.class);

                for (var hit : batchResponse.hits().hits()) {
                    Map<String, Object> doc = hit.source();
                    if (doc != null) {
                        // 封装数据（含商品列表）
                        Map<String, Object> orderVO = new HashMap<>();
                        orderVO.put("sub_order_id", doc.get("sub_order_id"));
                        orderVO.put("sub_order_sn", doc.get("sub_order_sn"));
                        orderVO.put("user_id", doc.get("user_id"));
                        orderVO.put("pay_amount", doc.get("pay_amount"));
                        orderVO.put("items", doc.get("items")); // 商品列表直接复制
                        allResults.add(orderVO);
                    }
                }
                currentFrom += batchSize;
            }

            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;

            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, " +
                    "总命中 " + totalHits + " 条, 实际封装 " + allResults.size() + " 条（含商品列表）");
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 3) + "ms");
        System.out.println("(ES 分批获取数据，每批1000条)");
        System.out.println("对比 MySQL：单次查询可返回全部数据");
        System.out.println("========================================");
    }

    /**
     * 步骤4：无用户过滤的全表搜索（模拟商家/后台场景）
     */
    @Test
    void testFullTableSearch() throws Exception {
        String keyword = "iPhone";

        System.out.println("\n========================================");
        System.out.println("ES 全表搜索性能测试（无用户过滤）");
        System.out.println("数据量：10万订单，30万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("========================================\n");

        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .size(100)
        );

        long totalTime = 0;

        System.out.println("查询条件：商品名包含 '" + keyword + "'（全平台所有用户）");
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            int count = (int) response.hits().total().value();
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 结果数 " + count);
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("========================================");
    }

    /**
     * 步骤4.1：纯商品名搜索（无用户过滤）- 对应 MySQL testFulltextNoJoin
     */
    @Test
    void testNestedQueryNoUserFilter() throws Exception {
        String keyword = "电脑";  // 与 MySQL 测试关键词一致

        System.out.println("\n========================================");
        System.out.println("ES Nested 查询性能测试（无用户过滤）");
        System.out.println("数据量：20万订单，60万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("对应 MySQL：testFulltextNoJoin");
        System.out.println("========================================\n");

        // 只搜索商品名，不加用户过滤
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));
            b.must(m -> m.nested(n -> n
                    .path("items")
                    .query(nq -> nq.match(mq -> mq
                            .field("items.spu_name")
                            .query(keyword)
                    ))
            ));
            return b;
        }));

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .size(100)
        );

        long totalTime = 0;

        System.out.println("查询条件：商品名包含 '" + keyword + "'（无用户过滤）");
        System.out.println("(一次查询完成，无JOIN开销)");
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            int count = (int) response.hits().total().value();
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 结果数 " + count);
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("========================================");
    }

    /**
     * 步骤5：根据订单ID查询 - 无搜索场景（对比MySQL主键查询）
     */
    @Test
    void testQueryByOrderId() throws Exception {
        Long orderId = 50000L;  // 测试中间位置的订单

        System.out.println("\n========================================");
        System.out.println("ES 根据订单ID查询性能测试（无搜索）");
        System.out.println("数据量：10万订单，30万商品");
        System.out.println("查询订单ID：" + orderId);
        System.out.println("========================================\n");

        // ES 的 Get 操作（类似MySQL主键查询，直接根据ID获取文档）
        long totalTime = 0;

        System.out.println("查询方式：GET by ID（直接获取文档）");
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            var response = client.get(g -> g
                    .index(INDEX_NAME)
                    .id(String.valueOf(orderId)),
                    Map.class
            );
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 找到文档: " + response.found());
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("(文档已包含订单+商品信息，无需二次查询)");
        System.out.println("========================================");
    }

    /**
     * 步骤6：订单号精确匹配 OR 商品名全文搜索
     * 对应 MySQL 的 UNION 查询
     */
    @Test
    void testOrderSnOrProductNameQuery() throws Exception {
        // 使用真实订单号（根据数据生成规则：2024 + 6位数字）
        String orderSn = "2024050000";  // 第50000个订单的订单号
        String productName = "iPhone";  // 商品名搜索关键词
        Long userId = 10500L;

        System.out.println("\n========================================");
        System.out.println("ES Bool Query 性能测试（订单号 OR 商品名）");
        System.out.println("数据量：10万订单，30万商品");
        System.out.println("订单号搜索：" + orderSn);
        System.out.println("商品名搜索：" + productName);
        System.out.println("用户ID：" + userId);
        System.out.println("查询逻辑：订单号精确匹配 OR 商品名全文搜索");
        System.out.println("========================================\n");

        // 嵌套 Bool Query：should + minimum_should_match: 1
        Query query = Query.of(q -> q.bool(b -> {
            // 用户过滤
            b.filter(f -> f.term(t -> t.field("user_id").value(userId)));
            // 逻辑删除过滤
            b.filter(f -> f.term(t -> t.field("is_delete").value(0)));

            // 核心：订单号 OR 商品名（至少匹配一个）
            b.must(m -> m.bool(sb -> {
                // 条件A：订单号精确匹配
                sb.should(s -> s.term(t -> t.field("sub_order_sn").value(orderSn)));
                // 条件B：商品名全文搜索
                sb.should(s -> s.nested(n -> n
                        .path("items")
                        .query(nq -> nq.match(mq -> mq
                                .field("items.spu_name")
                                .query(productName)
                        ))
                ));
                // 至少匹配一个
                sb.minimumShouldMatch("1");
                return sb;
            }));
            return b;
        }));

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(query)
                .size(100)
        );

        // 执行10次取平均值
        long totalTime = 0;

        System.out.println("查询条件：(订单号='" + orderSn + "' OR 商品名含'" + productName + "') + 用户ID=" + userId);
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            SearchResponse<Map> response = client.search(searchRequest, Map.class);
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            int count = (int) response.hits().total().value();
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 结果数 " + count);
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("(一次查询完成，无需 UNION 合并)");
        System.out.println("========================================");
    }
}