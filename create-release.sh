#!/bin/bash
# Create release package for distribution
# Version: 1.0

set -e

VERSION="1.0.0"
RELEASE_NAME="efficient-distributed-rv-${VERSION}"
RELEASE_DIR="release/${RELEASE_NAME}"

echo "Creating release package: ${RELEASE_NAME}"

# Clean previous release
rm -rf release
mkdir -p ${RELEASE_DIR}

# Build project
echo "→ Building project..."
mvn clean package -DskipTests -q

# Copy JAR
echo "→ Copying JAR..."
cp target/efficient-distributed-rv-1.0-SNAPSHOT-jar-with-dependencies.jar \
   ${RELEASE_DIR}/efficient-distributed-rv.jar

# Copy documentation
echo "→ Copying documentation..."
cp README.md USER_MANUAL.md INSTALL.md LICENSE ${RELEASE_DIR}/

# Copy examples
echo "→ Copying examples..."
mkdir -p ${RELEASE_DIR}/examples
cp src/main/java/Test.java ${RELEASE_DIR}/examples/
cp src/main/java/HighPerformanceLinearizabilityTest.java ${RELEASE_DIR}/examples/
cp src/main/java/NonLinearizableTest.java ${RELEASE_DIR}/examples/

# Copy run script
echo "→ Creating run script..."
cat > ${RELEASE_DIR}/run-example.sh << 'EOF'
#!/bin/bash
java -jar efficient-distributed-rv.jar
EOF
chmod +x ${RELEASE_DIR}/run-example.sh

# Create archive
echo "→ Creating archive..."
cd release
tar -czf ${RELEASE_NAME}.tar.gz ${RELEASE_NAME}
zip -r -q ${RELEASE_NAME}.zip ${RELEASE_NAME}
cd ..

# Summary
echo ""
echo "✓ Release package created:"
echo "  release/${RELEASE_NAME}.tar.gz"
echo "  release/${RELEASE_NAME}.zip"
echo ""
echo "Contents:"
echo "  - efficient-distributed-rv.jar (16MB)"
echo "  - README.md, USER_MANUAL.md, INSTALL.md, LICENSE"
echo "  - examples/ (3 example files)"
echo "  - run-example.sh"
echo ""
