package com.example.common.testing;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.testcontainers.core.CreateContainerCmdModifier;

/** Test-only resource policy shared by service modules through the erp-common test JAR. */
public class ContainerLimits implements CreateContainerCmdModifier {
    private static final Set<Integer> ALLOCATED = new HashSet<>();

    @Override
    public CreateContainerCmd modify(CreateContainerCmd cmd) {
        long memory = 512L * 1024 * 1024;
        cmd.withName("pu-erp-test-" + UUID.randomUUID());
        cmd.getHostConfig().withMemory(memory).withMemorySwap(memory).withNanoCPUs(2_000_000_000L);
        Ports ports = new Ports();
        if (cmd.getExposedPorts() != null) {
            for (ExposedPort port : cmd.getExposedPorts()) {
                ports.bind(port, Ports.Binding.bindIpAndPort("127.0.0.1", availablePort()));
            }
        }
        cmd.getHostConfig().withPortBindings(ports).withPublishAllPorts(false);
        return cmd;
    }

    private static synchronized int availablePort() {
        for (int attempt = 0; attempt < 1000; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(41000, 50000);
            if (ALLOCATED.contains(candidate)) continue;
            try (ServerSocket socket = new ServerSocket(candidate, 1, InetAddress.getByName("127.0.0.1"))) {
                ALLOCATED.add(candidate);
                return socket.getLocalPort();
            } catch (IOException busy) {
                // Try another high port. Docker will report a race with another process as a startup failure.
            }
        }
        throw new IllegalStateException("No free test port in 41000-49999");
    }
}
