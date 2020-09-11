package cz.elostech.h2db_operator;

import java.io.IOException;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;


public class H2DbOperator {

    private static final Logger log = LoggerFactory.getLogger(H2DbOperator.class);
    public static void main(String[] args) throws IOException {
        log.info("H2DB Operator starting!");

        Config config = new ConfigBuilder().withNamespace(null).build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        Operator operator = new Operator(client);
        operator.registerControllerForAllNamespaces(new H2DbController(client));

        new FtBasic(
                new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080
        ).start(Exit.NEVER);
    }

}