FROM alpine:3.5

RUN apk update && apk add openjdk8-jre tini

# Copy all dependencies
COPY lib/* /opt/reporting/lib/

# Copy configuration
COPY application.conf /opt/reporting/

# Copy reporting jar
COPY lib-bestmile/* /opt/reporting/lib/

# Copy entrypoint
COPY docker-entrypoint.sh /

CMD ["/sbin/tini", "--", "/docker-entrypoint.sh"]
