package com.example.xmpp.example;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
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
 * <p>演示如何基于共享模板配置，并仅根据不同 IP 建立多个连接。
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
        XmppClusterManager clusterManager = new XmppClusterManager(
                createBaseConfig(),
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

    private static XmppClientConfig createBaseConfig() {
        return XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("admin")
                .password("admin-password".toCharArray())
                .resource("cluster-console")
                .securityMode(XmppClientConfig.SecurityMode.REQUIRED)
                .port(5222)
                .connectTimeout(15000)
                .readTimeout(60000)
                .reconnectionEnabled(true)
                .reconnectionBaseDelay(5)
                .reconnectionMaxDelay(300)
                .pingEnabled(true)
                .pingInterval(30)
                .sendPresence(true)
                .usingDirectTLS(false)
                .enabledSaslMechanisms(java.util.Set.of("SCRAM-SHA-256", "PLAIN"))
                .build();
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
         * @param baseConfig 公共基础配置
         * @param nodes 节点定义列表
         */
        public XmppClusterManager(XmppClientConfig baseConfig, List<NodeDefinition> nodes) {
            Objects.requireNonNull(baseConfig, "baseConfig must not be null");
            Objects.requireNonNull(nodes, "nodes must not be null");

            Map<String, ManagedNode> nodeMap = new LinkedHashMap<>();
            for (NodeDefinition node : nodes) {
                XmppClientConfig config = XmppClientConfig.builder()
                        .xmppServiceDomain(baseConfig.getXmppServiceDomain())
                        .host(node.ip())
                        .resource(baseConfig.getResource())
                        .enabledSaslMechanisms(baseConfig.getEnabledSaslMechanisms())
                        .connectTimeout(baseConfig.getConnectTimeout())
                        .readTimeout(baseConfig.getReadTimeout())
                        .sendPresence(baseConfig.isSendPresence())
                        .username(baseConfig.getUsername())
                        .password(baseConfig.getPassword())
                        .authzid(baseConfig.getAuthzid())
                        .securityMode(baseConfig.getSecurityMode())
                        .customTrustManager(baseConfig.getCustomTrustManager())
                        .keyManagers(baseConfig.getKeyManagers())
                        .tlsAuthenticationMode(baseConfig.getTlsAuthenticationMode())
                        .customSslContext(baseConfig.getCustomSslContext())
                        .enabledSSLProtocols(baseConfig.getEnabledSSLProtocols())
                        .enabledSSLCiphers(baseConfig.getEnabledSSLCiphers())
                        .usingDirectTLS(baseConfig.isUsingDirectTLS())
                        .handshakeTimeoutMs(baseConfig.getHandshakeTimeoutMs())
                        .reconnectionEnabled(baseConfig.isReconnectionEnabled())
                        .reconnectionBaseDelay(baseConfig.getReconnectionBaseDelay())
                        .reconnectionMaxDelay(baseConfig.getReconnectionMaxDelay())
                        .pingEnabled(baseConfig.isPingEnabled())
                        .pingInterval(baseConfig.getPingInterval())
                        .port(baseConfig.getPort())
                        .build();
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
