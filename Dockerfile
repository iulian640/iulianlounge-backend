# Etapa 1: compilar con Maven. Los tests ya los pasa el CI; aquí solo se empaqueta
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Primero el pom y el wrapper: si solo cambia el código, Docker reutiliza la capa con las dependencias
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# Etapa 2: solo el JRE y el jar. Ni Maven, ni el código fuente, ni el JDK
FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios: si alguien rompiera la app, no sería root dentro del contenedor
RUN useradd --system --no-create-home lounge
COPY --from=build /src/target/*.jar app.jar
USER lounge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
