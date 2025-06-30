# Installation

This guide will help you install and set up RESTQ on your system.
You may use RESTQ both locally and on the remote server. If you'd like to use some of its components locally take a look at these prerequisites. In case of deployment to remote server, Ansible script is repsonsible for this. In this case, make sure you have Ansible installed.

## Prerequisites

Before installing RESTQ, ensure you have the following prerequisites installed:

### Required Dependencies

#### Java Development Kit (JDK)
RESTQ requires **Java 23** or later.

=== "Ubuntu/Debian"
    ```bash
    # Install OpenJDK 23
    sudo apt update
    sudo apt install openjdk-23-jdk
    
    # Verify installation
    java --version
    javac --version
    ```

=== "macOS"
    ```bash
    # Using Homebrew
    brew install openjdk@23
    
    # Add to PATH (add to ~/.zshrc or ~/.bash_profile)
    export PATH="/opt/homebrew/opt/openjdk@23/bin:$PATH"
    
    # Verify installation
    java --version
    ```

=== "Windows"
    1. Download OpenJDK 23 from [Adoptium](https://adoptium.net/)
    2. Run the installer
    3. Add Java to your PATH environment variable
    4. Verify: `java --version`

#### Apache Maven
Maven is required for building the Java components.

=== "Ubuntu/Debian"
    ```bash
    sudo apt install maven
    mvn --version
    ```

=== "macOS"
    ```bash
    brew install maven
    mvn --version
    ```

=== "Windows"
    1. Download Maven from [Apache Maven](https://maven.apache.org/download.cgi)
    2. Extract and add `bin` directory to PATH
    3. Verify: `mvn --version`

#### Docker
Docker is required for containerized deployments and energy monitoring.

=== "Ubuntu/Debian"
    ```bash
    # Install Docker
    sudo apt install docker.io docker-compose
    
    # Add user to docker group
    sudo usermod -aG docker $USER
    
    # Restart and verify
    sudo systemctl restart docker
    docker --version
    ```

=== "macOS"
    ```bash
    # Install Docker Desktop
    brew install --cask docker
    
    # Start Docker Desktop and verify
    docker --version
    ```

=== "Windows"
    1. Download and install [Docker Desktop](https://www.docker.com/products/docker-desktop)
    2. Verify: `docker --version`

#### Python (for Visualization)
Python 3.8+ is required for the visualization components.

=== "Ubuntu/Debian"
    ```bash
    sudo apt install python3 python3-pip python3-venv
    python3 --version
    ```

=== "macOS"
    ```bash
    # Python should be pre-installed, or use Homebrew
    brew install python3
    python3 --version
    ```

=== "Windows"
    1. Download Python from [python.org](https://www.python.org/downloads/)
    2. Install with "Add to PATH" option checked
    3. Verify: `python --version`

### Optional Dependencies

#### Ansible (for Multi-Node Deployment)
Required only if you plan to use the Ansible automation features.

```bash
pip3 install ansible
ansible --version
```

#### PostgreSQL Client Tools
Useful for database administration and testing.

=== "Ubuntu/Debian"
    ```bash
    sudo apt install postgresql-client
    ```

=== "macOS"
    ```bash
    brew install postgresql
    ```

=== "Windows"
    Download from [PostgreSQL website](https://www.postgresql.org/download/windows/)

## Installation Methods

### Method 1: Clone from Repository (Recommended)

```bash
# Clone the repository
git clone https://gitlab.com/your-org/rest-q.git
cd rest-q

# Build the core module
cd core
mvn clean install
cd ..

# Build the API module
cd api-http
mvn clean package
cd ..

# Set up Python visualization environment
cd visualisation
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
cd ..
```

### Method 2: Docker-Only Setup

If you prefer a containerized setup:

```bash
# Clone the repository
git clone https://gitlab.com/your-org/rest-q.git
cd rest-q

# Build and run with Docker Compose
docker-compose up -d
```

## Database Setup

### PostgreSQL with Docker

```bash
# Start PostgreSQL container
docker run -d \
  --name restq-postgres \
  -e POSTGRES_DB=benchmarks \
  -e POSTGRES_USER=benchuser \
  -e POSTGRES_PASSWORD=benchpass \
  -p 5432:5432 \
  postgres:4

# Verify connection
docker exec -it restq-postgres psql -U benchuser -d benchmarks -c "SELECT version();"
```

### Local PostgreSQL Installation

=== "Ubuntu/Debian"
    ```bash
    # Install PostgreSQL
    sudo apt install postgresql postgresql-contrib
    
    # Start service
    sudo systemctl start postgresql
    sudo systemctl enable postgresql
    
    # Create user and database
    sudo -u postgres createuser -P benchuser
    sudo -u postgres createdb -O benchuser benchmarks
    ```

=== "macOS"
    ```bash
    # Install PostgreSQL
    brew install postgresql@15
    brew services start postgresql@15
    
    # Create user and database
    createuser -P benchuser
    createdb -O benchuser benchmarks
    ```

## Verification

### Test Core Installation

```bash
cd core
mvn test
```

### Test API Installation

```bash
cd api-http
mvn test
```

### Test Database Connection

```bash
cd core
mvn exec:java -Dexec.mainClass="com.restq.InitDB" \
  -Dexec.args="--url jdbc:postgresql://localhost:5432/benchmarks --user benchuser --password benchpass"
```

### Test Visualization

```bash
cd visualisation
source venv/bin/activate
python visualizer_containers.py
```

## Next Steps

Once installation is complete:

1. **[Quick Start](quickstart.md)**: Run your first benchmark
2. **[Configuration](configuration.md)**: Customize your setup
3. **[User Guide](../user-guide/benchmarks.md)**: Learn to use rest-q effectively

## Troubleshooting

### Common Issues

#### Java Version Conflicts
```bash
# Check Java version
java --version

# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/java-23-openjdk-amd64
```

#### Maven Build Failures
```bash
# Clean and rebuild
mvn clean install -U

# Skip tests if needed
mvn clean install -DskipTests
```

#### Docker Permission Issues
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Restart shell or logout/login
```

#### Database Connection Issues
- Verify database is running: `docker ps` or `systemctl status postgresql`
- Check firewall rules for port 5432
- Verify credentials and database name

