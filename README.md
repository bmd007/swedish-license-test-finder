# swedish-license-test-finder
swedish-driving-license-test-finder


build: ./gradlew bootJar

run: java -jar -Dssn=[person number] ./build/libs/license-test-finder-beta.jar
