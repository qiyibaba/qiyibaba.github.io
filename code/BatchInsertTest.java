package com.qiyibaba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatchInsertTest {
    public static Connection getConnection(String url) {
        Connection conn = null;

        try {
            System.out.println("URL:" + url);
            String user = "root";
            String passwd = "db10$ZTE";
            Class.forName("com.mysql.jdbc.Driver");// 指定连接类型
            conn = DriverManager.getConnection(url, user, passwd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return conn;
    }

    public static void mainTest(int totalSize, int batchSize, String url) throws SQLException {
        Connection conn = getConnection(url);
        long start = System.currentTimeMillis();
        try {
            conn.setAutoCommit(false);
            PreparedStatement preparedStatement = conn.prepareStatement("insert into sbtest(id,k,c,pad) values (?,?,?,?)");
            int cycle = totalSize / batchSize;
            for (int j = 0; j < cycle; j++) {
                for (int i = 1; i <= batchSize; i++) {
                    preparedStatement.setInt(1, batchSize * j + i);
                    preparedStatement.setInt(2, (int) (Math.random() * 100000000));
                    preparedStatement.setString(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                    preparedStatement.setString(4, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                conn.commit();
                if (j % 100 == 0) {
                    System.out.println("commit " + j + " times,total need " + cycle + " times");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            conn.commit();
            conn.close();
        }
        long end = System.currentTimeMillis();
        System.out.println("insert [" + totalSize + "," + batchSize + "] lines total cost " + (end - start) + "ms");
    }

//    public static void main(String[] args) throws SQLException {
//        int t = 1000;
//        int b = 100;
//        String url = "jdbc:mysql://10.46.178.242:5519/sbtest1?useSSL=false&rewriteBatchedStatements=true";
//        new JdbcTest().mainGdbTest(2, t, b, url);
//    }

    public static void main(String[] args) throws SQLException {
        int gn = Integer.parseInt(args[0]);
        int t = Integer.parseInt(args[1]);
        int b = Integer.parseInt(args[2]);
        String url = args[4];
        if (gn > 1) {
            new BatchInsertTest().mainGdbTest(gn, t, b, url);
        } else {
            mainTest(t, b, url);
        }
    }

    public void mainGdbTest(int groupNum, int totalSize, int batchSize, String url) {
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(groupNum + 2);

        long start = System.currentTimeMillis();
        int egsize = totalSize / groupNum;
        for (int i = 0; i < groupNum; i++) {
            fixedThreadPool.execute(new GoldenDBInsert(i * egsize + 1, (i + 1) * egsize, batchSize, url));
        }
        fixedThreadPool.shutdown();

        while (!fixedThreadPool.isTerminated()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("insert [" + totalSize + "," + batchSize + "] lines total cost " + (end - start) + "ms");
    }

    public static class GoldenDBInsert implements Runnable {

        private int start;
        private int end;
        private int batchSize;
        private String url;

        public GoldenDBInsert(int start, int end, int batchSize, String url) {
            this.start = start;
            this.end = end;
            this.batchSize = batchSize;
            this.url = url;
        }

        @Override
        public void run() {
            System.out.println("begin to insert data from " + start + " to " + end);
            Connection conn = getConnection(url);
            try {
                conn.setAutoCommit(false);
                PreparedStatement preparedStatement = conn.prepareStatement("insert into sbtest(id,k,c,pad) values (?,?,?,?)");
                int cycle = (end - start + 1) / batchSize;
                for (int j = 0; j < cycle; j++) {
                    for (int i = 0; i < batchSize; i++) {
                        preparedStatement.setInt(1, start + batchSize * j + i);
                        preparedStatement.setInt(2, (int) (Math.random() * 100000000));
                        preparedStatement.setString(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                        preparedStatement.setString(4, "ABCDEFGHIJKLMNOPQRSTUVWXYZ-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                    conn.commit();
                    if (j % 100 == 0) {
                        System.out.println("commit " + j + " times,total need " + cycle + " times");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    conn.commit();
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
