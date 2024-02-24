# these instructions are for junit.
# jcodec also works but needs an edit to pom.xml; details at end of this file.
# the run will be rather slow (approximately half an hour) because it's a local search per hot method
# drop lines from the junit4.Profiler_output.csv to reduce the number of searches to be run (generally best to leave the first few as they're the hottest methods)


# clone and build gin
git clone git@github.com:sandybrownlee/gin-llm.git
cd gin-llm
git checkout llm
./gradlew clean build


# prepare target project
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
git clone git@github.com:junit-team/junit4.git
cd junit4
git checkout r4.13.2
mvn clean compile
mvn clean test

# get list of target/hot methods from profiling
wget -O junit4.Profiler_output.csv "https://drive.google.com/uc?export=download&id=1wdKwk1DHpDqvHvPPxzPHEVALumzvbF_1" # junit4

# run search
projectnameforgin='junit4'
java -Dtinylog.level=trace -cp ../build/gin.jar gin.LLMExample -j -p $projectnameforgin -d . -m $projectnameforgin.Profiler_output.csv -o $projectnameforgin.LocalSearchRuntime_LLM_10_output.csv -h ~/.sdkman/candidates/maven/current -timeoutMS 10000 -in 10 -oaik demo -tpt1 ../examples/llm/base-template-prompt.txt -tpt2 ../examples/llm/base-template-prompt-with-error.txt &> $projectnameforgin.LLMExample_10_stderrstdout.txt

# breaking down that command:
#java -Dtinylog.level=trace -cp ../build/gin.jar gin.LLMExample 
#-j   - runs a separate JVM for the unit tests (rather than in the same JVM as Gin - safer for multithreaded applications); change to -J to make a separate JVM for every unit test
#-p $projectnameforgin  - name of the target project
#-d .     - working directory (location of the target project)
#-m $projectnameforgin.Profiler_output.csv    - CSV containing hot methods for the project, and associated unit tests
#-o $projectnameforgin.LocalSearchRuntime_LLM_10_output.csv    - where to write the results
#-h ~/.sdkman/candidates/maven/current    - location of maven
#-timeoutMS 10000    - timeout for unit tests
#-in 10    - how many edits to try (i.e., iterations of the local search)
#-oaik demo    - openAI key
#-tpt1 ../examples/llm/base-template-prompt.txt               - prompt template 1 - without error feedback
#-tpt2 ../examples/llm/base-template-prompt-with-error.txt    - prompt template 2 - with error feedback




# other project: can also use jcodec
#git clone git@github.com:jcodec/jcodec.git
#cd jcodec
# *** then need to edit pom.xml - change lines 92/93 <source> <target> from 1.6 to 1.8 ***
#wget -O jcodec.Profiler_output.csv "https://drive.google.com/uc?export=download&id=14AjHX4h4GqaJTxIhbwTm5P--sRhI1kXX" # jcodec
#projectnameforgin='jcodec'
