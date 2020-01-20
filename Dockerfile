FROM openjdk:8

RUN apt update
RUN apt install sudo
RUN sudo apt install maven -y
COPY ./src/ /home/misows/src
COPY ./pom.xml /home/misows/

RUN mvn -f /home/misows/pom.xml clean package
RUN cp /home/misows/target/misows.jar /usr/local/lib/misows.jar
ENTRYPOINT ["java", "-jar", "/usr/local/lib/misows.jar"]
