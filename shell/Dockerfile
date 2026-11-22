FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash ttyd python3 python3-pip python3-venv git curl wget ca-certificates \
    nano vim-tiny jq dnsutils iputils-ping net-tools nmap whois openssl procps \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /bin/bash sentinel \
    && mkdir -p /workspace && chown -R sentinel:sentinel /workspace /home/sentinel
USER sentinel
WORKDIR /workspace
EXPOSE 7681
CMD ["ttyd","--writable","--port","7681","bash","-l"]
