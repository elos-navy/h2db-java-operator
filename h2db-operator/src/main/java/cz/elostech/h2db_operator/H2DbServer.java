package cz.elostech.h2db_operator;

import io.fabric8.kubernetes.client.CustomResource;

public class H2DbServer extends CustomResource {
    
    private H2DbServerSpec spec;

    private H2DbServerStatus status;

    public H2DbServerSpec getSpec() {
        return spec;
    }

    public void setSpec(H2DbServerSpec spec) {
        this.spec = spec;
    }

    public H2DbServerStatus getStatus() {
        return status;
    }

    public void setStatus(H2DbServerStatus status) {
        this.status = status;
    }
}
