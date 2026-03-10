package com.example.xmpp.example;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.SystemService;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.config.XmppConfigKeys;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 多连接统一配置示例。
 *
 * <p>演示如何通过 {@link SystemService} 统一获取公共配置，并仅根据不同 IP 建立多个连接。
 * 每个连接都会启用自动重连，并为每个连接创建独立的 {@link AdminManager}。</p>
 *
 * @since 2026-03-11
 */
@Slf4j
public final class MultiConnectionAdminExample {

    private MultiConnectionAdminExample() {
    }

    /**
     * 示例入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SystemService systemService = createSystemServiceBean();
        XmppClusterManager clusterManager = new XmppClusterManager(
                systemService,
                List.of(
                        new NodeDefinition("node-a", "192.168.10.11"),
                        new NodeDefinition("node-b", "192.168.10.12"),
                        new NodeDefinition("node-c", "192.168.10.13")
                )
        );

        try {
            clusterManager.connectAll();

            clusterManager.addUserOnAll("ops-demo", "ChangeMe123!", "ops-demo@example.com").join();
            clusterManager.changePassword("node-a", "ops-demo", "ChangeMe456!").join();
            clusterManager.getOnlineUsers("node-b").join();
            clusterManager.listUsers("node-c").join();
        } finally {
            clusterManager.disconnectAll();
        }
    }

    private static SystemService createSystemServiceBean() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(XmppConfigKeys.XMPP_SERVICE_DOMAIN, "example.com");
        values.put(XmppConfigKeys.USERNAME, "admin");
        values.put(XmppConfigKeys.PASSWORD, "admin-password");
        values.put(XmppConfigKeys.RESOURCE, "cluster-console");
        values.put(XmppConfigKeys.SECURITY_MODE, XmppClientConfig.SecurityMode.REQUIRED.name());
        values.put(XmppConfigKeys.PORT, "5222");
        values.put(XmppConfigKeys.CONNECT_TIMEOUT, "15000");
        values.put(XmppConfigKeys.READ_TIMEOUT, "60000");
        values.put(XmppConfigKeys.RECONNECTION_ENABLED, "true");
        values.put(XmppConfigKeys.RECONNECTION_BASE_DELAY, "5");
        values.put(XmppConfigKeys.RECONNECTION_MAX_DELAY, "300");
        values.put(XmppConfigKeys.PING_ENABLED, "true");
        values.put(XmppConfigKeys.PING_INTERVAL, "30");
        values.put(XmppConfigKeys.SEND_PRESENCE, "true");
        values.put(XmppConfigKeys.DIRECT_TLS, "false");
        values.put(XmppConfigKeys.ENABLED_SASL_MECHANISMS, "SCRAM-SHA-256,PLAIN");
        return values::get;
    }

    private record NodeDefinition(String nodeId, String ip) {
    }

    private record ManagedNode(String nodeId, String ip, XmppTcpConnection connection, AdminManager adminManager) {
    }

    /**
     * 多连接管理器示例实现。
     */
    @Slf4j
    public static final class XmppClusterManager {

        private final Map<String, ManagedNode> managedNodes;

        /**
         * 创建多连接管理器。
         *
         * @param systemService 公共配置 Bean
         * @param nodes 节点定义列表
         */
        public XmppClusterManager(SystemService systemService, List<NodeDefinition> nodes) {
            Objects.requireNonNull(systemService, "systemService must not be null");
            Objects.requireNonNull(nodes, "nodes must not be null");

            Map<String, ManagedNode> nodeMap = new LinkedHashMap<>();
            for (NodeDefinition node : nodes) {
                XmppClientConfig config = XmppClientConfig.fromSystemService(systemService, node.ip());
                XmppTcpConnection connection = new XmppTcpConnection(config);
                AdminManager adminManager = new AdminManager(connection, config);
                nodeMap.put(node.nodeId(), new ManagedNode(node.nodeId(), node.ip(), connection, adminManager));
            }
            this.managedNodes = Map.copyOf(nodeMap);
        }

        /**
         * 连接所有节点。
         */
        public void connectAll() {
            for (ManagedNode managedNode : managedNodes.values()) {
                try {
                    managedNode.connection().connect();
                    log.info("Connected node {} at {}", managedNode.nodeId(), managedNode.ip());
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Failed to connect node " + managedNode.nodeId() + " (" + managedNode.ip() + ")", e);
                }
            }
        }

        /**
         * 断开所有节点连接。
         */
        public void disconnectAll() {
            for (ManagedNode managedNode : managedNodes.values()) {
                try {
                    managedNode.connection().disconnect();
                    log.info("Disconnected node {}", managedNode.nodeId());
                } catch (RuntimeException e) {
                    log.warn("Disconnect node {} failed: {}", managedNode.nodeId(), e.getMessage(), e);
                }
            }
        }

        /**
         * 在所有节点上创建用户。
         *
         * @param username 用户名
         * @param password 密码
         * @param email 邮箱
         * @return 所有节点操作完成的 Future
         */
        public CompletableFuture<Void> addUserOnAll(String username, String password, String email) {
            return runOnAll(node -> node.adminManager().addUser(username, password, email));
        }

        /**
         * 在指定节点修改用户密码。
         *
         * @param nodeId 节点标识
         * @param username 用户名
         * @param newPassword 新密码
         * @return 管理命令结果
         */
        public CompletableFuture<XmppStanza> changePassword(String nodeId, String username, String newPassword) {
            return getNode(nodeId).adminManager().changePassword(username, newPassword);
        }

        /**
         * 查询指定节点的在线用户。
         *
         * @param nodeId 节点标识
         * @return 管理命令结果
         */
        public CompletableFuture<XmppStanza> getOnlineUsers(String nodeId) {
            return getNode(nodeId).adminManager().getOnlineUsers();
        }

        /**
         * 查询指定节点的用户列表。
         *
         * @param nodeId 节点标识
         * @return 管理命令结果
         */
        public CompletableFuture<XmppStanza> listUsers(String nodeId) {
            return getNode(nodeId).adminManager().listUsers();
        }

        private CompletableFuture<Void> runOnAll(Function<ManagedNode, CompletableFuture<XmppStanza>> operation) {
            CompletableFuture<?>[] futures = managedNodes.values()
                    .stream()
                    .map(operation)
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures);
        }

        private ManagedNode getNode(String nodeId) {
            ManagedNode managedNode = managedNodes.get(nodeId);
            if (managedNode == null) {
                throw new IllegalArgumentException("Unknown nodeId: " + nodeId);
            }
            return managedNode;
        }
    }
}