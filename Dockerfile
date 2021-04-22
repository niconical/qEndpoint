
# Set the base image to node:12-alpine
FROM node:12-alpine as build-frontend

# Specify where our app will live in the container
WORKDIR /home/app

COPY hdt-qs-frontend/package.json /home/app/package.json
# Prepare the container for building React
RUN npm install

# Copy the React App to the container
COPY hdt-qs-frontend/. /home/app/

# Build for production version
RUN npm run build

#
# Build backend stage
#
FROM maven:3.6.0-jdk-11-slim AS build-backend

WORKDIR /home/app
# install dependencies
COPY hdt-qs-backend/pom.xml .
COPY hdt-qs-backend/ci_settings.xml .


RUN mvn dependency:go-offline -s ci_settings.xml --quiet

# build the application
COPY hdt-qs-backend/allatori /home/app/allatori
COPY hdt-qs-backend/src /home/app/src
COPY --from=build-frontend /home/app/build/ /home/app/src/resources/main/resources/static/

RUN mvn -f /home/app/pom.xml clean package -s /home/app/ci_settings.xml --quiet -DskipTests -P prod

#
# Build frontend stage
#



# Package stage
#
FROM openjdk:11-jre-slim

WORKDIR /home/app
# init dirs
RUN mkdir data
RUN mkdir data/hdt-store

COPY --from=build-backend /home/app/target/hdtSparqlEndpoint-*-SNAPSHOT-obf.jar /usr/local/lib/hdtSparqlEndpoint-*-SNAPSHOT-obf.jar
COPY --from=build-backend /home/app/src/main/resources/application-prod.properties /home/app/application-prod.properties

EXPOSE 1234
RUN  apt-get update \
  && apt-get install -y wget \
  && rm -rf /var/lib/apt/lists/*

COPY loadData.sh .
RUN chmod +x loadData.sh
CMD ./loadData.sh