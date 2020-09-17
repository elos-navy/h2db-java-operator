package cz.elostech.h2db_operator;

public class H2DbServerSpec {
    private String image;
    private int svc_port = 1521;
    private boolean persistent = false;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public int getSvc_port() {
        return svc_port;
    }

    public void setSvc_port(int svc_port) {
        this.svc_port = svc_port;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

}
