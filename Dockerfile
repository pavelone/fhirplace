FROM ubuntu:14.04
MAINTAINER Nikolay Ryzhikov <niquola@gmail.com>, Maksym Bodnarchuk <bodnarchuk@gmail.com>

RUN apt-get -qq update
RUN apt-get install -qq -y software-properties-common curl
RUN apt-get -qqy install git build-essential
RUN add-apt-repository ppa:webupd8team/java && apt-get -qq update
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -qq -y oracle-java7-installer
RUN useradd -d /home/fhir -m -s /bin/bash fhir && echo "fhir:fhir"|chpasswd && adduser fhir sudo

RUN echo 'fhir ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

USER fhir
ENV HOME /home/fhir
RUN cd /home/fhir && mkdir -p /home/fhir/bin && curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /home/fhir/bin/lein && chmod a+x /home/fhir/bin/lein
RUN /home/fhir/bin/lein
env PATH /home/fhir/bin:$PATH

# All commands will rebuild each time above this line.

ADD . /home/fhir/fhirplace
RUN sudo chown -R fhir:fhir /home/fhir/fhirplace
RUN cd /home/fhir/fhirplace && rm -rf .git && git init && git submodule init && git submodule update
RUN cd /home/fhir/fhirplace && lein deps

EXPOSE 8080

ENV FHIRPLACE_WEB_PORT  8080
ENV FHIRPLACE_SUBPROTOCOL postgres
ENV FHIRPLACE_DBHOST  fhirbase.io
ENV FHIRPLACE_DATABASE  fhirbase
ENV FHIRPLACE_USER fhirbase
ENV FHIRPLACE_PASSWORD fhirbase

CMD export FHIRPLACE_SUBNAME="//$FHIRPLACE_DBHOST/$FHIRPLACE_DATABASE" \
    && cd ~/fhirplace \
    && lein run
