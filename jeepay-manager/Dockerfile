#
#   jeepay-manager（运营平台） java程序镜像构建脚本
#

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG BASE_IMAGE=swr.cn-south-1.myhuaweicloud.com/jeepay/eclipse-temurin:17-jre

# Maven 构建阶段：在 Docker 内完成 Java 编译
FROM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace

COPY . /workspace

# 配置 Maven 阿里云镜像源，加速国内依赖下载
RUN mkdir -p /root/.m2 && printf '<settings>\n  <mirrors>\n    <mirror>\n      <id>aliyun</id>\n      <mirrorOf>central</mirrorOf>\n      <url>https://maven.aliyun.com/repository/public</url>\n    </mirror>\n  </mirrors>\n</settings>\n' > /root/.m2/settings.xml

RUN mvn -pl jeepay-manager -am -DskipTests package

# Eclipse Temurin JRE 17（openjdk 官方镜像已停止维护）
FROM ${BASE_IMAGE}

LABEL maintainer="Terrfly"

ENV LANG=C.UTF-8
ENV TZ=Asia/Shanghai

EXPOSE 9217

VOLUME ["/jeepayhomes/service/logs", "/jeepayhomes/service/uploads"]

RUN mkdir -p /jeepayhomes/service/app

COPY --from=builder /workspace/jeepay-manager/target/jeepay-manager.jar /jeepayhomes/service/app/jeepay-manager.jar

WORKDIR /jeepayhomes/service/app

CMD ["java", "-jar", "jeepay-manager.jar", "--spring.config.additional-location=file:/jeepayhomes/service/app/application.yml"]

# 端口探测，不依赖 curl/wget 等外部工具
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'echo > /dev/tcp/localhost/9217'
