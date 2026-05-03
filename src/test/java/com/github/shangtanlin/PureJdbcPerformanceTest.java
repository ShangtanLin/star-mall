package com.github.shangtanlin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 查询性能测试（纯 JDBC，不启动 Spring）
 */
public class PureJdbcPerformanceTest {

    private static Connection connection;

    @BeforeAll
    static void setup() throws Exception {
        // 直接连接 MySQL，不依赖 Spring Boot
        // 根据你的 application.yml 修改配置
        String url = "jdbc:mysql://localhost:3306/taobao-mall?useSSL=false&serverTimezone=Asia/Shanghai";
        String username = "root";
        String password = "123456";

        connection = DriverManager.getConnection(url, username, password);
        System.out.println("MySQL 连接成功！");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * 全文索引 + JOIN 查询性能测试
     */
    @Test
    void testFulltextJoinQuery() throws Exception {
        String keyword = "电脑";
        //String keyword = "iPhone";
        Long userId = 10005L;

        //String keyword = "iPhone";
        //Long userId = 10005L;

        String sql = """
            SELECT DISTINCT so.id, so.sub_order_sn, so.user_id
            FROM test_sub_order so
            INNER JOIN test_sub_order_item oi ON so.id = oi.sub_order_id
            WHERE MATCH(oi.spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
              AND so.user_id = ?
              AND so.is_delete = 0
            """;

        System.out.println("\n========================================");
        System.out.println("全文索引 + JOIN 查询性能测试");
        System.out.println("数据量：20万订单，60万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("========================================\n");

        System.out.println("查询条件：商品名含 '" + keyword + "' + 用户ID=" + userId);
        System.out.println("(需要JOIN订单表过滤用户)");
        System.out.println();

        // 执行10次取平均值
        long totalTime = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQuery(sql, keyword, userId);
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            int count = results.size();
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 结果数 " + count);
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("========================================");
    }

    /**
     * 纯全文索引查询（无JOIN）- 直接查商品表
     */
    @Test
    void testFulltextNoJoin() throws Exception {
        String keyword = "电脑";

        String sql = """
            SELECT id, sub_order_id, spu_id, spu_name, price
            FROM test_sub_order_item
            WHERE MATCH(spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
            """;

        System.out.println("\n========================================");
        System.out.println("纯全文索引查询（无JOIN）性能测试");
        System.out.println("数据量：60万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("========================================\n");

        System.out.println("查询条件：商品名含 '" + keyword + "'");
        System.out.println("(无JOIN，直接查商品表)");
        System.out.println();

        // 执行10次取平均值
        long totalTime = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeItemQuery(sql, keyword);
            long end = System.currentTimeMillis();
            long elapsed = end - start;
            totalTime += elapsed;
            int count = results.size();
            System.out.println("第 " + i + " 次查询: 耗时 " + elapsed + "ms, 结果数 " + count);
        }

        System.out.println("\n========================================");
        System.out.println("平均耗时: " + (totalTime / 10) + "ms");
        System.out.println("========================================");
    }


    /**
     * 根据订单ID查询 - 无JOIN场景（MySQL的主键查询优势）
     */
    @Test
    void testQueryByOrderIdNoJoin() throws Exception {
        Long orderId = 50000L;  // 测试中间位置的订单

        System.out.println("\n========================================");
        System.out.println("根据订单ID查询（无JOIN）性能测试");
        System.out.println("数据量：10万订单，30万商品");
        System.out.println("查询订单ID：" + orderId);
        System.out.println("========================================\n");

        // 测试1：查订单主表（主键查询）
        String sqlOrder = """
            SELECT id, sub_order_sn, user_id, status, pay_amount
            FROM test_sub_order
            WHERE id = ?
            """;

        System.out.println("--- 测试1：查订单主表 ---");
        long totalTime1 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQueryById(sqlOrder, orderId);
            long end = System.currentTimeMillis();
            totalTime1 += (end - start);
            System.out.println("第 " + i + " 次查询耗时: " + (end - start) + "ms");
        }
        System.out.println("订单主表平均耗时: " + (totalTime1 / 10) + "ms\n");

        // 测试2：查订单项表（索引查询）
        String sqlItems = """
            SELECT id, sub_order_id, spu_id, spu_name, price, quantity
            FROM test_sub_order_item
            WHERE sub_order_id = ?
            """;

        System.out.println("--- 测试2：查订单项表 ---");
        long totalTime2 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQueryById(sqlItems, orderId);
            long end = System.currentTimeMillis();
            totalTime2 += (end - start);
            System.out.println("第 " + i + " 次查询耗时: " + (end - start) + "ms, 结果数: " + results.size());
        }
        System.out.println("订单项表平均耗时: " + (totalTime2 / 10) + "ms\n");

        // 测试3：两次查询合并（模拟应用层组装）
        System.out.println("--- 测试3：两次查询合并（模拟无JOIN场景） ---");
        long totalTime3 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            executeQueryById(sqlOrder, orderId);
            executeQueryById(sqlItems, orderId);
            long end = System.currentTimeMillis();
            totalTime3 += (end - start);
            System.out.println("第 " + i + " 次总耗时: " + (end - start) + "ms");
        }

        System.out.println("\n========================================");
        System.out.println("订单主表平均耗时: " + (totalTime1 / 10) + "ms");
        System.out.println("订单项表平均耗时: " + (totalTime2 / 10) + "ms");
        System.out.println("两次查询合并平均耗时: " + (totalTime3 / 10) + "ms");
        System.out.println("========================================");
    }

    private List<Map<String, Object>> executeUnionQuery(String sql, Object... params) throws Exception {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }

        ResultSet rs = stmt.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("sub_order_sn", rs.getString("sub_order_sn"));
            row.put("user_id", rs.getLong("user_id"));
            row.put("status", rs.getInt("status"));
            results.add(row);
        }

        rs.close();
        stmt.close();
        return results;
    }

    private List<Map<String, Object>> executeQueryById(String sql, Long id) throws Exception {
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setLong(1, id);

        ResultSet rs = stmt.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            results.add(row);
        }

        rs.close();
        stmt.close();
        return results;
    }

    private List<Map<String, Object>> executeQuery(String sql, Object... params) throws Exception {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }

        ResultSet rs = stmt.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("sub_order_sn", rs.getString("sub_order_sn"));
            row.put("user_id", rs.getLong("user_id"));
            results.add(row);
        }

        rs.close();
        stmt.close();
        return results;
    }


    /**
     * 为用户 10005 批量插入 1万条订单数据
     * 用于测试单个用户数据量大时的查询性能
     */
    @Test
    void testBatchInsertOrdersForUser10005() throws Exception {
        Long targetUserId = 10005L;
        int orderCount = 10000;
        int itemsPerOrder = 3;

        System.out.println("\n========================================");
        System.out.println("批量插入测试数据");
        System.out.println("目标用户ID：" + targetUserId);
        System.out.println("订单数量：" + orderCount);
        System.out.println("每订单商品数：" + itemsPerOrder);
        System.out.println("预计商品总数：" + (orderCount * itemsPerOrder));
        System.out.println("========================================\n");

        long start = System.currentTimeMillis();

        // 关闭自动提交，使用批量插入
        connection.setAutoCommit(false);

        // 商品名池（模拟真实商品）
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

        // 批量插入订单
        String insertOrderSql = """
            INSERT INTO test_sub_order (sub_order_sn, user_id, shop_id, status, is_delete, pay_amount, create_time)
            VALUES (?, ?, 888, 1, 0, ?, NOW())
            """;

        PreparedStatement orderStmt = connection.prepareStatement(insertOrderSql, PreparedStatement.RETURN_GENERATED_KEYS);

        // 批量插入商品（先准备语句）
        String insertItemSql = """
            INSERT INTO test_sub_order_item (sub_order_id, spu_id, sku_id, spu_name, sku_name, price, quantity)
            VALUES (?, ?, ?, ?, ?, ?, 1)
            """;

        PreparedStatement itemStmt = connection.prepareStatement(insertItemSql);

        int batchCount = 0;
        long lastOrderId = 0;

        // 先获取当前最大订单ID
        String maxIdSql = "SELECT MAX(id) FROM test_sub_order";
        PreparedStatement maxIdStmt = connection.prepareStatement(maxIdSql);
        ResultSet maxIdRs = maxIdStmt.executeQuery();
        if (maxIdRs.next()) {
            lastOrderId = maxIdRs.getLong(1);
        }
        maxIdRs.close();
        maxIdStmt.close();

        System.out.println("当前最大订单ID：" + lastOrderId);
        System.out.println("开始插入...");

        for (int i = 1; i <= orderCount; i++) {
            // 订单号：2024BATCH + 6位数字
            String orderSn = "2024BATCH" + String.format("%06d", i);
            double payAmount = 1000 + (i % 100) * 50; // 模拟金额变化

            // 添加订单到批次
            orderStmt.setString(1, orderSn);
            orderStmt.setLong(2, targetUserId);
            orderStmt.setDouble(3, payAmount);
            orderStmt.addBatch();

            // 每500条提交一次批次
            if (i % 500 == 0) {
                orderStmt.executeBatch();
                connection.commit();

                // 获取刚插入的订单ID范围
                ResultSet generatedKeys = orderStmt.getGeneratedKeys();
                List<Long> newOrderIds = new ArrayList<>();
                while (generatedKeys.next()) {
                    newOrderIds.add(generatedKeys.getLong(1));
                }
                generatedKeys.close();

                // 为这批订单插入商品
                for (Long orderId : newOrderIds) {
                    for (int j = 0; j < itemsPerOrder; j++) {
                        String productName = productNames[j % productNames.length];
                        double price = 500 + (j + 1) * 200;

                        itemStmt.setLong(1, orderId);
                        itemStmt.setLong(2, 10000 + j);
                        itemStmt.setLong(3, 10000 + j);
                        itemStmt.setString(4, productName);
                        itemStmt.setString(5, productName + " 标准版");
                        itemStmt.setDouble(6, price);
                        itemStmt.addBatch();
                    }
                }
                itemStmt.executeBatch();
                connection.commit();

                batchCount++;
                System.out.println("已插入第 " + (batchCount * 500) + " 条订单及其商品...");
            }
        }

        // 处理剩余的订单（不足500条的部分）
        if (orderCount % 500 != 0) {
            orderStmt.executeBatch();
            connection.commit();

            ResultSet generatedKeys = orderStmt.getGeneratedKeys();
            List<Long> newOrderIds = new ArrayList<>();
            while (generatedKeys.next()) {
                newOrderIds.add(generatedKeys.getLong(1));
            }
            generatedKeys.close();

            for (Long orderId : newOrderIds) {
                for (int j = 0; j < itemsPerOrder; j++) {
                    String productName = productNames[j % productNames.length];
                    double price = 500 + (j + 1) * 200;

                    itemStmt.setLong(1, orderId);
                    itemStmt.setLong(2, 10000 + j);
                    itemStmt.setLong(3, 10000 + j);
                    itemStmt.setString(4, productName);
                    itemStmt.setString(5, productName + " 标准版");
                    itemStmt.setDouble(6, price);
                    itemStmt.addBatch();
                }
            }
            itemStmt.executeBatch();
            connection.commit();
        }

        orderStmt.close();
        itemStmt.close();
        connection.setAutoCommit(true);

        long end = System.currentTimeMillis();
        System.out.println("\n========================================");
        System.out.println("插入完成！");
        System.out.println("订单总数：" + orderCount);
        System.out.println("商品总数：" + (orderCount * itemsPerOrder));
        System.out.println("总耗时：" + (end - start) + "ms");
        System.out.println("========================================");
    }


    /**
     * 子查询优化方案：强制先过滤用户，再全文搜索
     * 解决 MySQL 优化器优先选择全文索引导致"中间膨胀"的问题
     */
    @Test
    void testSubqueryOptimization() throws Exception {
        String keyword = "电脑";
        Long userId = 10005L;

        System.out.println("\n========================================");
        System.out.println("子查询优化方案性能测试");
        System.out.println("强制先过滤用户，再全文搜索");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("用户订单数：约10208条");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("========================================\n");

        // 方案1：原始 JOIN + 全文索引（MySQL 优化器可能优先选全文索引）
        String sqlOriginal = """
            SELECT DISTINCT so.id, so.sub_order_sn, so.user_id
            FROM test_sub_order so
            INNER JOIN test_sub_order_item oi ON so.id = oi.sub_order_id
            WHERE MATCH(oi.spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
              AND so.user_id = ?
              AND so.is_delete = 0
            """;

        System.out.println("--- 方案1：原始 JOIN + 全文索引 ---");
        System.out.println("执行顺序：全文索引 → JOIN → 过滤用户（中间膨胀）");
        long totalTime1 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQuery(sqlOriginal, keyword, userId);
            long end = System.currentTimeMillis();
            totalTime1 += (end - start);
            System.out.println("第 " + i + " 次查询: 耗时 " + (end - start) + "ms, 结果数 " + results.size());
        }
        System.out.println("平均耗时: " + (totalTime1 / 10) + "ms\n");

        // 方案2：子查询强制先过滤用户（DERIVED TABLE）
        String sqlSubquery = """
            SELECT DISTINCT so.id, so.sub_order_sn, so.user_id
            FROM (
                SELECT id, sub_order_sn, user_id
                FROM test_sub_order
                WHERE user_id = ?
                  AND is_delete = 0
            ) so
            INNER JOIN test_sub_order_item oi ON so.id = oi.sub_order_id
            WHERE MATCH(oi.spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
            """;

        System.out.println("--- 方案2：子查询强制先过滤用户 ---");
        System.out.println("执行顺序：过滤用户 → JOIN → 全文索引（缩小范围）");
        long totalTime2 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQuerySubquery(sqlSubquery, userId, keyword);
            long end = System.currentTimeMillis();
            totalTime2 += (end - start);
            System.out.println("第 " + i + " 次查询: 耗时 " + (end - start) + "ms, 结果数 " + results.size());
        }
        System.out.println("平均耗时: " + (totalTime2 / 10) + "ms\n");

        // 方案3：EXISTS 子查询（另一种优化思路）
        String sqlExists = """
            SELECT so.id, so.sub_order_sn, so.user_id
            FROM test_sub_order so
            WHERE so.user_id = ?
              AND so.is_delete = 0
              AND EXISTS (
                  SELECT 1 FROM test_sub_order_item oi
                  WHERE oi.sub_order_id = so.id
                    AND MATCH(oi.spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
              )
            """;

        System.out.println("--- 方案3：EXISTS 子查询 ---");
        System.out.println("执行顺序：过滤用户 → EXISTS 全文索引检查");
        long totalTime3 = 0;
        for (int i = 1; i <= 10; i++) {
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = executeQuerySubquery(sqlExists, userId, keyword);
            long end = System.currentTimeMillis();
            totalTime3 += (end - start);
            System.out.println("第 " + i + " 次查询: 耗时 " + (end - start) + "ms, 结果数 " + results.size());
        }
        System.out.println("平均耗时: " + (totalTime3 / 10) + "ms\n");

        System.out.println("========================================");
        System.out.println("性能对比总结：");
        System.out.println("  方案1（原始 JOIN）：平均 " + (totalTime1 / 10) + "ms");
        System.out.println("  方案2（子查询）：   平均 " + (totalTime2 / 10) + "ms");
        System.out.println("  方案3（EXISTS）：   平均 " + (totalTime3 / 10) + "ms");
        System.out.println();
        System.out.println("核心差异：执行顺序不同，中间数据量不同");
        System.out.println("========================================");
    }

    /**
     * 真实业务场景：商家查询店铺订单列表第一页
     * 使用"先查店铺订单ID + 再全文索引 + LIMIT分页"的方式
     * 参数：keyword="iPhone", shopId=888, pageNo=1, pageSize=10
     */
    @Test
    void testShopOrderQueryWithFulltext() throws Exception {
        String keyword = "iPhone";
        Long shopId = 2001L;
        int pageNo = 1;
        int pageSize = 10;
        int offset = (pageNo - 1) * pageSize;

        System.out.println("\n========================================");
        System.out.println("MySQL 商家查询店铺订单测试（商家查看订单列表第一页）");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("店铺ID：" + shopId);
        System.out.println("页码：" + pageNo);
        System.out.println("每页条数：" + pageSize);
        System.out.println("分页参数：LIMIT " + offset + ", " + pageSize);
        System.out.println("========================================\n");

        System.out.println("查询方式：先查店铺订单ID → 再全文索引搜索 + LIMIT分页 → 封装数据");
        System.out.println();

        // 执行10次取平均值
        long totalQueryTime = 0;
        long totalPackageTime = 0;
        int totalHits = 0;

        for (int i = 1; i <= 10; i++) {
            // ① 查店铺的所有订单ID（用于限定全文索引搜索范围）
            long queryStep1Start = System.currentTimeMillis();
            String sqlOrderIds = """
                SELECT id
                FROM test_sub_order
                WHERE shop_id = ?
                  AND is_delete = 0
                """;
            PreparedStatement stmt1 = connection.prepareStatement(sqlOrderIds);
            stmt1.setLong(1, shopId);
            ResultSet rs1 = stmt1.executeQuery();

            List<Long> orderIds = new ArrayList<>();
            while (rs1.next()) {
                orderIds.add(rs1.getLong("id"));
            }
            rs1.close();
            stmt1.close();
            long queryStep1End = System.currentTimeMillis();

            // ② 全文索引搜索商品 + LIMIT分页
            long queryStep2Start = System.currentTimeMillis();
            List<Long> matchedOrderIds = new ArrayList<>();
            if (!orderIds.isEmpty()) {
                StringBuilder inClause = new StringBuilder();
                for (int j = 0; j < orderIds.size(); j++) {
                    inClause.append(orderIds.get(j));
                    if (j < orderIds.size() - 1) inClause.append(",");
                }

                String sqlSearch = """
                    SELECT DISTINCT sub_order_id
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                      AND MATCH(spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
                    LIMIT %d, %d
                    """.formatted(inClause, offset, pageSize);

                PreparedStatement stmt2 = connection.prepareStatement(sqlSearch);
                stmt2.setString(1, keyword);
                ResultSet rs2 = stmt2.executeQuery();

                while (rs2.next()) {
                    matchedOrderIds.add(rs2.getLong("sub_order_id"));
                }
                rs2.close();
                stmt2.close();

                // 查询总数
                String sqlCount = """
                    SELECT COUNT(DISTINCT sub_order_id) as total
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                      AND MATCH(spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
                    """.formatted(inClause);
                PreparedStatement stmtCount = connection.prepareStatement(sqlCount);
                stmtCount.setString(1, keyword);
                ResultSet rsCount = stmtCount.executeQuery();
                if (rsCount.next()) {
                    totalHits = rsCount.getInt("total");
                }
                rsCount.close();
                stmtCount.close();
            }
            long queryStep2End = System.currentTimeMillis();

            // ③ 查匹配订单的详情 + 商品列表
            long queryStep3Start = System.currentTimeMillis();
            List<Map<String, Object>> finalResults = new ArrayList<>();
            if (!matchedOrderIds.isEmpty()) {
                StringBuilder orderInClause = new StringBuilder();
                for (int j = 0; j < matchedOrderIds.size(); j++) {
                    orderInClause.append(matchedOrderIds.get(j));
                    if (j < matchedOrderIds.size() - 1) orderInClause.append(",");
                }

                String sqlOrderDetail = """
                    SELECT id, sub_order_sn, user_id, shop_id, status, pay_amount, create_time
                    FROM test_sub_order
                    WHERE id IN (%s)
                    """.formatted(orderInClause);

                PreparedStatement stmt3 = connection.prepareStatement(sqlOrderDetail);
                ResultSet rs3 = stmt3.executeQuery();

                Map<Long, Map<String, Object>> orderMap = new HashMap<>();
                while (rs3.next()) {
                    Long orderId = rs3.getLong("id");
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", orderId);
                    order.put("sub_order_sn", rs3.getString("sub_order_sn"));
                    order.put("user_id", rs3.getLong("user_id"));
                    order.put("shop_id", rs3.getLong("shop_id"));
                    order.put("status", rs3.getInt("status"));
                    order.put("pay_amount", rs3.getBigDecimal("pay_amount"));
                    order.put("create_time", rs3.getTimestamp("create_time"));
                    orderMap.put(orderId, order);
                }
                rs3.close();
                stmt3.close();

                // 查商品列表
                String sqlItemsDetail = """
                    SELECT sub_order_id, spu_id, spu_name, sku_name, price, quantity
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                    """.formatted(orderInClause);

                PreparedStatement stmt4 = connection.prepareStatement(sqlItemsDetail);
                ResultSet rs4 = stmt4.executeQuery();

                Map<Long, List<Map<String, Object>>> itemsByOrder = new HashMap<>();
                while (rs4.next()) {
                    Long orderId = rs4.getLong("sub_order_id");
                    Map<String, Object> item = new HashMap<>();
                    item.put("spu_id", rs4.getLong("spu_id"));
                    item.put("spu_name", rs4.getString("spu_name"));
                    item.put("sku_name", rs4.getString("sku_name"));
                    item.put("price", rs4.getBigDecimal("price"));
                    item.put("quantity", rs4.getInt("quantity"));
                    itemsByOrder.computeIfAbsent(orderId, k -> new ArrayList<>()).add(item);
                }
                rs4.close();
                stmt4.close();

                // 合并订单+商品
                for (Long orderId : matchedOrderIds) {
                    Map<String, Object> order = orderMap.get(orderId);
                    if (order != null) {
                        order.put("items", itemsByOrder.getOrDefault(orderId, new ArrayList<>()));
                        finalResults.add(order);
                    }
                }
            }
            long queryStep3End = System.currentTimeMillis();

            long queryTime = (queryStep1End - queryStep1Start) +
                              (queryStep2End - queryStep2Start) +
                              (queryStep3End - queryStep3Start);

            totalQueryTime += queryTime;
            totalPackageTime += 0;

            System.out.println("第 " + i + " 次查询: 查询用时 " + queryTime + "ms, " +
                    "总命中 " + totalHits + " 条, 返回 " + finalResults.size() + " 条（含商品列表）");
        }

        System.out.println("\n========================================");
        System.out.println("平均查询用时: " + (totalQueryTime / 10) + "ms");
        System.out.println();
        System.out.println("查询步骤分解：");
        System.out.println("  ① 查店铺订单：WHERE shop_id = " + shopId);
        System.out.println("  ② 全文索引搜索 + LIMIT：搜索商品名含 '" + keyword + "'");
        System.out.println("  ③ 查订单详情 + 商品列表：只查分页后的10条");
        System.out.println();
        System.out.println("对比用户查询：");
        System.out.println("  用户查询：WHERE user_id = xxx（某用户的订单）");
        System.out.println("  商家查询：WHERE shop_id = xxx（某店铺的订单）");
        System.out.println("  商家订单量可能更大，查询耗时可能更长");
        System.out.println("========================================");
    }

    /**
     * 真实业务场景：用户查询订单列表第一页
     * 使用"先查订单ID + 再全文索引 + LIMIT分页"的方式（与 ES 测试对应）
     * 参数：keyword="iPhone", userId=10005, pageNo=1, pageSize=10
     */
    @Test
    void testRealBusinessWithFulltext() throws Exception {
        String keyword = "iPhone";
        Long userId = 10005L;
        int pageNo = 1;
        int pageSize = 10;
        int offset = (pageNo - 1) * pageSize;  // 第一页：offset = 0

        System.out.println("\n========================================");
        System.out.println("MySQL 真实业务场景测试（用户查看订单列表第一页）");
        System.out.println("数据量：21万订单，63万商品");
        System.out.println("用户订单数：约10208条");
        System.out.println("搜索关键词：" + keyword);
        System.out.println("用户ID：" + userId);
        System.out.println("页码：" + pageNo);
        System.out.println("每页条数：" + pageSize);
        System.out.println("分页参数：LIMIT " + offset + ", " + pageSize);
        System.out.println("========================================\n");

        System.out.println("查询方式：先查用户订单ID → 再全文索引搜索 + LIMIT分页 → 封装数据");
        System.out.println();

        // 执行10次取平均值
        long totalQueryTime = 0;   // 纯查询用时
        long totalPackageTime = 0; // 封装用时
        int totalHits = 0;

        for (int i = 1; i <= 10; i++) {
            // ========== 第一步：查询阶段 ==========

            // ① 查用户的所有订单ID（用于限定全文索引搜索范围）
            long queryStep1Start = System.currentTimeMillis();
            String sqlOrderIds = """
                SELECT id
                FROM test_sub_order
                WHERE user_id = ?
                  AND is_delete = 0
                """;
            PreparedStatement stmt1 = connection.prepareStatement(sqlOrderIds);
            stmt1.setLong(1, userId);
            ResultSet rs1 = stmt1.executeQuery();

            List<Long> orderIds = new ArrayList<>();
            while (rs1.next()) {
                orderIds.add(rs1.getLong("id"));
            }
            rs1.close();
            stmt1.close();
            long queryStep1End = System.currentTimeMillis();

            // ② 全文索引搜索商品 + LIMIT分页（关键：数据库层面分页）
            long queryStep2Start = System.currentTimeMillis();
            List<Long> matchedOrderIds = new ArrayList<>();
            if (!orderIds.isEmpty()) {
                StringBuilder inClause = new StringBuilder();
                for (int j = 0; j < orderIds.size(); j++) {
                    inClause.append(orderIds.get(j));
                    if (j < orderIds.size() - 1) inClause.append(",");
                }

                // 关键：使用 LIMIT 分页，只返回需要的10条订单ID
                String sqlSearch = """
                    SELECT DISTINCT sub_order_id
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                      AND MATCH(spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
                    LIMIT %d, %d
                    """.formatted(inClause, offset, pageSize);

                PreparedStatement stmt2 = connection.prepareStatement(sqlSearch);
                stmt2.setString(1, keyword);
                ResultSet rs2 = stmt2.executeQuery();

                while (rs2.next()) {
                    matchedOrderIds.add(rs2.getLong("sub_order_id"));
                }
                rs2.close();
                stmt2.close();

                // 查询总数（用于分页显示）
                String sqlCount = """
                    SELECT COUNT(DISTINCT sub_order_id) as total
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                      AND MATCH(spu_name) AGAINST(? IN NATURAL LANGUAGE MODE)
                    """.formatted(inClause);
                PreparedStatement stmtCount = connection.prepareStatement(sqlCount);
                stmtCount.setString(1, keyword);
                ResultSet rsCount = stmtCount.executeQuery();
                if (rsCount.next()) {
                    totalHits = rsCount.getInt("total");
                }
                rsCount.close();
                stmtCount.close();
            }
            long queryStep2End = System.currentTimeMillis();

            // ③ 查匹配订单的详情 + 商品列表（只查这10条）
            long queryStep3Start = System.currentTimeMillis();
            List<Map<String, Object>> finalResults = new ArrayList<>();
            if (!matchedOrderIds.isEmpty()) {
                // 查订单详情
                StringBuilder orderInClause = new StringBuilder();
                for (int j = 0; j < matchedOrderIds.size(); j++) {
                    orderInClause.append(matchedOrderIds.get(j));
                    if (j < matchedOrderIds.size() - 1) orderInClause.append(",");
                }

                String sqlOrderDetail = """
                    SELECT id, sub_order_sn, user_id, status, pay_amount, create_time
                    FROM test_sub_order
                    WHERE id IN (%s)
                    """.formatted(orderInClause);

                PreparedStatement stmt3 = connection.prepareStatement(sqlOrderDetail);
                ResultSet rs3 = stmt3.executeQuery();

                Map<Long, Map<String, Object>> orderMap = new HashMap<>();
                while (rs3.next()) {
                    Long orderId = rs3.getLong("id");
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", orderId);
                    order.put("sub_order_sn", rs3.getString("sub_order_sn"));
                    order.put("user_id", rs3.getLong("user_id"));
                    order.put("status", rs3.getInt("status"));
                    order.put("pay_amount", rs3.getBigDecimal("pay_amount"));
                    order.put("create_time", rs3.getTimestamp("create_time"));
                    orderMap.put(orderId, order);
                }
                rs3.close();
                stmt3.close();

                // 查商品列表
                String sqlItemsDetail = """
                    SELECT sub_order_id, spu_id, spu_name, sku_name, price, quantity
                    FROM test_sub_order_item
                    WHERE sub_order_id IN (%s)
                    """.formatted(orderInClause);

                PreparedStatement stmt4 = connection.prepareStatement(sqlItemsDetail);
                ResultSet rs4 = stmt4.executeQuery();

                Map<Long, List<Map<String, Object>>> itemsByOrder = new HashMap<>();
                while (rs4.next()) {
                    Long orderId = rs4.getLong("sub_order_id");
                    Map<String, Object> item = new HashMap<>();
                    item.put("spu_id", rs4.getLong("spu_id"));
                    item.put("spu_name", rs4.getString("spu_name"));
                    item.put("sku_name", rs4.getString("sku_name"));
                    item.put("price", rs4.getBigDecimal("price"));
                    item.put("quantity", rs4.getInt("quantity"));
                    itemsByOrder.computeIfAbsent(orderId, k -> new ArrayList<>()).add(item);
                }
                rs4.close();
                stmt4.close();

                // 合并订单+商品
                for (Long orderId : matchedOrderIds) {
                    Map<String, Object> order = orderMap.get(orderId);
                    if (order != null) {
                        order.put("items", itemsByOrder.getOrDefault(orderId, new ArrayList<>()));
                        finalResults.add(order);
                    }
                }
            }
            long queryStep3End = System.currentTimeMillis();

            long queryTime = (queryStep1End - queryStep1Start) +
                              (queryStep2End - queryStep2Start) +
                              (queryStep3End - queryStep3Start);

            // ========== 第二步：封装阶段（已在查询中完成）==========
            long packageTime = 0;  // 封装已在查询阶段完成

            totalQueryTime += queryTime;
            totalPackageTime += packageTime;

            System.out.println("第 " + i + " 次查询: 查询用时 " + queryTime + "ms, " +
                    "封装用时 " + packageTime + "ms, 总用时 " + queryTime + "ms, " +
                    "总命中 " + totalHits + " 条, 返回 " + finalResults.size() + " 条（含商品列表）");
        }

        System.out.println("\n========================================");
        System.out.println("平均查询用时: " + (totalQueryTime / 10) + "ms");
        System.out.println("平均封装用时: " + (totalPackageTime / 10) + "ms");
        System.out.println("平均总用时: " + (totalQueryTime / 10) + "ms");
        System.out.println();
        System.out.println("查询步骤分解（正确的数据库分页）：");
        System.out.println("  ① 查用户订单ID：约 1万个订单ID");
        System.out.println("  ② 全文索引搜索 + LIMIT " + offset + ", " + pageSize + "：只返回10条匹配的订单ID");
        System.out.println("  ③ 查订单详情 + 商品列表：只查这10条订单");
        System.out.println("对比 ES：ES 文档自带商品列表，无需第③步");
        System.out.println("========================================");
    }

    private List<Map<String, Object>> executeQuerySubquery(String sql, Object... params) throws Exception {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }

        ResultSet rs = stmt.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("sub_order_sn", rs.getString("sub_order_sn"));
            row.put("user_id", rs.getLong("user_id"));
            results.add(row);
        }

        rs.close();
        stmt.close();
        return results;
    }

    private List<Map<String, Object>> executeItemQuery(String sql, Object... params) throws Exception {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }

        ResultSet rs = stmt.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("sub_order_id", rs.getLong("sub_order_id"));
            row.put("spu_id", rs.getLong("spu_id"));
            row.put("spu_name", rs.getString("spu_name"));
            row.put("price", rs.getDouble("price"));
            results.add(row);
        }

        rs.close();
        stmt.close();
        return results;
    }

    /**
     * 普通 LIMIT 深分页测试
     * 测试不同页码下，普通 LIMIT 的性能变化
     * 对比您之前测试的"全文索引 + LIMIT"方案
     */
    @Test
    void testNormalLimitDeepPagination() throws Exception {
        System.out.println("\n========================================");
        System.out.println("MySQL 普通 LIMIT 深分页测试");
        System.out.println("数据量：21万订单");
        System.out.println("测试不同页码的查询耗时");
        System.out.println("========================================\n");

        // 测试不同页码：第1页、第10页、第100页、第1000页、第10000页
        int[] pageNos = {1, 10, 100, 1000, 10000};
        int pageSize = 10;

        for (int pageNo : pageNos) {
            int offset = (pageNo - 1) * pageSize;

            System.out.println("--- 第 " + pageNo + " 页（LIMIT " + offset + ", " + pageSize + ") ---");

            // 普通 LIMIT 查询（无全文索引，无 IN 子句限定范围）
            String sql = """
                SELECT id, sub_order_sn, user_id, status, pay_amount, create_time
                FROM test_sub_order
                ORDER BY id
                LIMIT %d, %d
                """.formatted(offset, pageSize);

            // 执行5次取平均值
            long totalTime = 0;
            for (int i = 1; i <= 5; i++) {
                long start = System.currentTimeMillis();
                PreparedStatement stmt = connection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sub_order_sn", rs.getString("sub_order_sn"));
                    row.put("user_id", rs.getLong("user_id"));
                    row.put("status", rs.getInt("status"));
                    row.put("pay_amount", rs.getBigDecimal("pay_amount"));
                    row.put("create_time", rs.getTimestamp("create_time"));
                    results.add(row);
                }

                rs.close();
                stmt.close();
                long end = System.currentTimeMillis();
                long elapsed = end - start;
                totalTime += elapsed;
                System.out.println("  第 " + i + " 次查询: 耗时 " + elapsed + "ms, 返回 " + results.size() + " 条");
            }

            System.out.println("  平均耗时: " + (totalTime / 5) + "ms\n");
        }

        System.out.println("========================================");
        System.out.println("深分页性能总结：");
        System.out.println("  如果耗时随页码增加明显上升 → 说明存在深分页问题");
        System.out.println("  如果耗时基本不变 → 说明无明显深分页问题");
        System.out.println();
        System.out.println("对比您之前的测试（全文索引 + LIMIT）：");
        System.out.println("  全文索引先限定范围（约1万条），LIMIT 在小范围内跳过");
        System.out.println("  普通 LIMIT 在全表21万条中跳过，差异明显");
        System.out.println("========================================");
    }

    /**
     * 非主键排序的深分页测试
     * ORDER BY create_time（非主键字段）
     * 对比主键排序的深分页性能差异
     */
    @Test
    void testNonPrimaryKeyDeepPagination() throws Exception {
        System.out.println("\n========================================");
        System.out.println("MySQL 非主键排序深分页测试");
        System.out.println("数据量：21万订单");
        System.out.println("ORDER BY create_time（非主键字段）");
        System.out.println("对比主键排序的深分页性能差异");
        System.out.println("========================================\n");

        // 测试不同页码
        int[] pageNos = {1, 10, 100, 1000, 10000};
        int pageSize = 10;

        for (int pageNo : pageNos) {
            int offset = (pageNo - 1) * pageSize;

            System.out.println("--- 第 " + pageNo + " 页（LIMIT " + offset + ", " + pageSize + ") ---");

            // 非主键排序的 LIMIT 查询
            String sql = """
                SELECT id, sub_order_sn, user_id, status, pay_amount, create_time
                FROM test_sub_order
                ORDER BY create_time
                LIMIT %d, %d
                """.formatted(offset, pageSize);

            // 执行5次取平均值
            long totalTime = 0;
            for (int i = 1; i <= 5; i++) {
                long start = System.currentTimeMillis();
                PreparedStatement stmt = connection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("sub_order_sn", rs.getString("sub_order_sn"));
                    row.put("user_id", rs.getLong("user_id"));
                    row.put("status", rs.getInt("status"));
                    row.put("pay_amount", rs.getBigDecimal("pay_amount"));
                    row.put("create_time", rs.getTimestamp("create_time"));
                    results.add(row);
                }

                rs.close();
                stmt.close();
                long end = System.currentTimeMillis();
                long elapsed = end - start;
                totalTime += elapsed;
                System.out.println("  第 " + i + " 次查询: 耗时 " + elapsed + "ms, 返回 " + results.size() + " 条");
            }

            System.out.println("  平均耗时: " + (totalTime / 5) + "ms\n");
        }

        System.out.println("========================================");
        System.out.println("深分页性能总结（非主键排序）：");
        System.out.println();
        System.out.println("对比两种排序方式：");
        System.out.println("  主键排序（ORDER BY id）：");
        System.out.println("    → 聚簇索引直接定位，跳过 offset 条只需多走几步");
        System.out.println("    → 深分页问题不明显（1ms → 44ms）");
        System.out.println();
        System.out.println("  非主键排序（ORDER BY create_time）：");
        System.out.println("    → 先在 create_time 索引中找位置");
        System.out.println("    → 再回表查每条完整数据（大量随机 I/O）");
        System.out.println("    → 深分页问题明显，耗时应该大幅增加");
        System.out.println("========================================");
    }
}