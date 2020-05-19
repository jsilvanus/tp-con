@echo off
javac --release 8 TPConnector.java
javac --release 8 ExamplePlugin.java
java TPConnector
java ExamplePlugin testone
java ExamplePlugin testtwo
echo NOTE: Copying classes to plugin path:
copy *.class ExamplePlugin\
