FROM gradle:5.6.2-jdk12 as builder

LABEL company="Vlad Karachev."
LABEL version="0.1.0"

ENV LANG=C.UTF-8 \
  REFRESHED_AT=2019-09-14-1
ENV DEBIAN_FRONTEND noninteractive

WORKDIR /opt/server

COPY . .
RUN mv gradle-container.properties gradle.properties
RUN gradle shadowJar

#############################################################

FROM openjdk:14-jdk-alpine3.10

LABEL company="Vlad Karachev."
LABEL version="0.1.0"

ENV LANG=C.UTF-8 \
  REFRESHED_AT=2019-09-14-1
ENV DEBIAN_FRONTEND noninteractive

RUN apk add --update \
  bash

WORKDIR /opt/server/

COPY --from=builder /opt/server/profiles/build/libs/profiles-0.1.0-all.jar ./app.jar
COPY --from=builder /opt/server/conf ./conf
COPY --from=builder /opt/server/ssl ./ssl

EXPOSE 50051 5701

CMD exec java -jar app.jar
