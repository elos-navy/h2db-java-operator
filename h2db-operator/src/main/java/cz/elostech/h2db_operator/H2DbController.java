package cz.elostech.h2db_operator;

import java.io.IOException;
import java.io.InputStream;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.utils.Serialization;

@Controller(customResourceClass = H2DbServer.class, crdName = "h2dbs.operators.elostech.cz")
public class H2DbController implements ResourceController<H2DbServer> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;

    public H2DbController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }


    @Override
    public boolean deleteResource(H2DbServer resource, Context<H2DbServer> context) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public UpdateControl<H2DbServer> createOrUpdateResource(H2DbServer h2dbServer, Context<H2DbServer> context) {
        
        String namespace = h2dbServer.getMetadata().getNamespace();
        String image = h2dbServer.getSpec().getImage();
        Deployment deployment = loadYaml(Deployment.class, "h2db-deployment.yaml");

        deployment.getMetadata().setName(deploymentName(h2dbServer));
        deployment.getMetadata().setNamespace(namespace);
        deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName(h2dbServer));
        deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName(h2dbServer));
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(image);

        log.info("Creating or updating Deployment {} in {}", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        H2DbServerStatus status = new H2DbServerStatus();
        status.setImage(image);
        status.setAreWeGood("Yes!");
        h2dbServer.setStatus(status);
//        throw new RuntimeException("Creating object failed, because it failed");
        return UpdateControl.updateCustomResource(h2dbServer);
    }

    private static String deploymentName(H2DbServer h2) {
        return h2.getMetadata().getName();
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
    
}
