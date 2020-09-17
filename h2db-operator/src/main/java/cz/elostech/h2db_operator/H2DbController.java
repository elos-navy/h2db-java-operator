package cz.elostech.h2db_operator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
    public boolean deleteResource(H2DbServer h2DbServer, Context<H2DbServer> context) {
        log.info("Execution deleteResource for: {}", h2DbServer.getMetadata().getName());

        log.info("Deleting Deployment {}", deploymentName(h2DbServer));
        RollableScalableResource<Deployment, DoneableDeployment> deployment = kubernetesClient.apps().deployments()
                .inNamespace(h2DbServer.getMetadata().getNamespace())
                .withName(deploymentName(h2DbServer));
        if (deployment.get() != null) {
            deployment.cascading(true).delete();
        }
        
        log.info("Deleting Service {}", serviceName(h2DbServer));
        ServiceResource<Service, DoneableService> service = kubernetesClient.services()
                .inNamespace(h2DbServer.getMetadata().getNamespace())
                .withName(serviceName(h2DbServer));
        if (service.get() != null) {
            service.delete();
        }

        log.info("Deleting PVC {}", pvcName(h2DbServer));
        Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim> pvc = kubernetesClient.persistentVolumeClaims()
                .inNamespace(h2DbServer.getMetadata().getNamespace())
                .withName(pvcName(h2DbServer));
        if (pvc.get() != null) {
            pvc.delete();
        }

        return true;
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

        log.info("Preparing Deployment {} in {}", deployment.getMetadata().getName(), namespace);
        // kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        // Override port setting with CR value
        List<ServicePort> ports = new ArrayList<ServicePort>(1);
        ports.add(new ServicePortBuilder()
            .withNewName("jdbc")
            .withNewProtocol("TCP")
            .withNewPort(h2dbServer.getSpec().getSvc_port())
            .withNewTargetPort(1521)
            .build());

        Service service = loadYaml(Service.class, "h2db-jdbc-service.yaml");
        service.getMetadata().setName(serviceName(h2dbServer));
        service.getMetadata().setNamespace(namespace);
        service.getMetadata().setLabels(deployment.getSpec().getTemplate().getMetadata().getLabels());
        service.getSpec().setSelector(deployment.getSpec().getTemplate().getMetadata().getLabels());
        service.getSpec().setPorts(ports);
        
        if (kubernetesClient.services().inNamespace(namespace).withName(service.getMetadata().getName()).get() == null) 
            log.info("Creating Service {} in {}", service.getMetadata().getName(), namespace);
        else
            log.info("Updating Service {} in {}", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);

        // persistence logic
        Boolean persist = h2dbServer.getSpec().isPersistent();
        log.debug("H2DB persistence is set to: {}", persist);
        // kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(pvcName(h2dbServer)).get() == null
        if (persist) {
            PersistentVolumeClaim pvc = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).createOrReplaceWithNew()
                .withNewMetadata().withName(pvcName(h2dbServer))
                .withLabels(deployment.getSpec().getTemplate().getMetadata().getLabels()).endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity("10Mi")) // TODO externalize to CRD
                .endResources()
                .endSpec()
                .done();
            
            log.info("PVC in {} (re)created...", namespace);
            Volume v = new Volume();
            v.setName("h2db-data");
            v.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(pvcName(h2dbServer), false));
            List<Volume> volumes = new ArrayList<Volume>(1);
            volumes.add(v);
            deployment.getSpec().getTemplate().getSpec()
                .setVolumes(volumes);

        } else {
            log.info("Switching off persistence to ephemeral.");
            deployment.getSpec().getTemplate().getSpec()
                .getVolumes().get(0).setEmptyDir(new EmptyDirVolumeSource()); // TODO limit with resources/quantity
            log.info("Deleting PVC {}", pvcName(h2dbServer));
            Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim> pvc = kubernetesClient.persistentVolumeClaims()
                    .inNamespace(h2dbServer.getMetadata().getNamespace())
                    .withName(pvcName(h2dbServer));
            if (pvc.get() != null) {
                pvc.delete();
            }            
        }
        log.info("Creating/Updating Deployment {} in {}", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        H2DbServerStatus status = new H2DbServerStatus();
        status.setImage(image);
        status.setAreWeGood("Yes!");
        h2dbServer.setStatus(status);   

        return UpdateControl.updateCustomResource(h2dbServer);
    }

    private static String deploymentName(H2DbServer h2) {
        return h2.getMetadata().getName().concat("-deployment");
    }
    
    private static String serviceName(H2DbServer h2) {
        return h2.getMetadata().getName().concat("-svc");
    }

    private static String pvcName(H2DbServer h2) {
        return h2.getMetadata().getName().concat("-pvc");
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
    
}
