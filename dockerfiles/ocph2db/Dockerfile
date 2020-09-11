FROM fabric8/java-centos-openjdk8-jre:latest

ENV H2_URL https://repo1.maven.org
ENV H2_VERSION 1.4.200

USER 0

ADD ${H2_URL}/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar /tmp/h2-${H2_VERSION}.jar

RUN mkdir -p /opt/h2 \
	&& mv /tmp/h2-${H2_VERSION}.jar /opt/h2/h2-${H2_VERSION}.jar \
	&& chmod -R 744 /opt/h2

USER 1001

EXPOSE 8081 1521

VOLUME ["/opt/h2-data"]

CMD java -cp /opt/h2/h2*.jar org.h2.tools.Server \
 	-web -webAllowOthers -webPort 8081 \
 	-tcp -tcpAllowOthers -tcpPort 1521 \
 	-baseDir /opt/h2-data