FROM fabric8/java-centos-openjdk8-jre:latest

ENV URL https://h2database.com/h2-2019-10-14.zip

USER 0

RUN yum -y install unzip \
	&& curl ${URL} -o /tmp/h2.zip \
    && unzip /tmp/h2.zip -d /opt/ \
	&& chmod -R 777 /opt/h2 \
    && rm /tmp/h2.zip 

USER 1001
EXPOSE 8081 1521

VOLUME ["/opt/h2-data"]

CMD java -cp /opt/h2/bin/h2*.jar org.h2.tools.Server \
 	-web -webAllowOthers -webPort 8081 \
 	-tcp -tcpAllowOthers -tcpPort 1521 \
 	-baseDir /opt/h2-data