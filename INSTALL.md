# Installation Guide

**Version:** 1.0
**Last Updated:** December 2025

---

## System Requirements

### Minimum Requirements

- **Java:** 21 or higher
- **Maven:** 3.6 or higher
- **Memory:** 2GB RAM
- **Disk Space:** 500MB

### Recommended Requirements

- **Java:** 21 (latest patch)
- **Maven:** 3.9 or higher
- **Memory:** 4GB RAM
- **Disk Space:** 1GB

### Supported Operating Systems

- Linux (Ubuntu 20.04+, CentOS 8+, etc.)
- macOS (11.0+)
- Windows (10+)

---

## Installation Steps

### Step 1: Install Java 21

**Check if Java 21 is installed:**
```bash
java -version
```

**If not installed:**

**On Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

**On macOS (using Homebrew):**
```bash
brew install openjdk@21
```

**On Windows:**
Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [Adoptium](https://adoptium.net/)

### Step 2: Install Maven

**Check if Maven is installed:**
```bash
mvn -version
```

**If not installed:**

**On Ubuntu/Debian:**
```bash
sudo apt install maven
```

**On macOS (using Homebrew):**
```bash
brew install maven
```

**On Windows:**
Download from [Apache Maven](https://maven.apache.org/download.cgi)

### Step 3: Clone Repository

```bash
git clone <repository-url>
cd efficient-distributed-rv
```

### Step 4: Build Project

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Create JAR
mvn package
```

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
```

### Step 5: Verify Installation

```bash
# Run basic test
./run-test.sh
```

**Expected output:**
```
Is linearizable: true
Execution time: X ms
Operations: 100
```

---

## Installation Verification

### Test 1: Compile Main Code

```bash
mvn compile
```

Should complete without errors.

### Test 2: Run Tests

```bash
mvn test
```

Should show: `Tests run: 78, Failures: 0, Errors: 0`

### Test 3: Run Example

```bash
java -cp "target/classes:$(mvn dependency:build-classpath -q)" Test
```

Should print linearizability result.

---

## Troubleshooting

### Problem: "Java version not supported"

**Error:**
```
[ERROR] Failed to execute goal ... source release 21 requires target release 21
```

**Solution:**
Ensure Java 21 is installed and JAVA_HOME is set:
```bash
export JAVA_HOME=/path/to/java21
export PATH=$JAVA_HOME/bin:$PATH
```

### Problem: "Maven not found"

**Solution:**
Install Maven (see Step 2 above) or download manually from Apache Maven website.

### Problem: "Tests fail"

**Solution:**
Check if all dependencies downloaded:
```bash
mvn dependency:resolve
mvn clean test
```

### Problem: "OutOfMemoryError during build"

**Solution:**
Increase Maven memory:
```bash
export MAVEN_OPTS="-Xmx2g"
mvn clean install
```

### Problem: "Scala compilation fails"

**Solution:**
This is normal if Scala tests have issues. Main Java code should still compile:
```bash
mvn compile -DskipTests
```

---

## Building Distribution JAR

### Create Fat JAR with Dependencies

```bash
mvn clean package
```

JAR location: `target/efficient-distributed-rv-1.0-SNAPSHOT.jar`

### Run from JAR

```bash
java -cp target/efficient-distributed-rv-1.0-SNAPSHOT.jar Test
```

---

## IDE Setup

### IntelliJ IDEA

1. Open IntelliJ IDEA
2. File → Open → Select project directory
3. Wait for Maven import to complete
4. Right-click `pom.xml` → Maven → Reload Project

### Eclipse

1. Open Eclipse
2. File → Import → Maven → Existing Maven Projects
3. Select project directory
4. Finish

### VS Code

1. Install "Extension Pack for Java"
2. Open project folder
3. VS Code will auto-detect Maven project

---

## Uninstallation

```bash
# Remove build artifacts
mvn clean

# Remove entire project
cd ..
rm -rf efficient-distributed-rv
```

---

## Next Steps

After installation:

1. Read [USER_MANUAL.md](USER_MANUAL.md) for usage instructions
2. Try examples in `src/main/java/`
3. Run `./run-test.sh` to verify everything works

---

## Getting Help

- **Issues:** Check [GitHub Issues](repository-url/issues)
- **Documentation:** See [USER_MANUAL.md](USER_MANUAL.md)
- **Email:** your.email@example.com

---

**Installation complete!** You're ready to verify linearizability.
