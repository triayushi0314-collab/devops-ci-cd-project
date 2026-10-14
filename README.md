# End-to-End CI/CD Pipeline for a Java Web Application

A DevOps internship project that automates the full delivery path of a Java web application: **Git -> Maven -> Jenkins -> Apache Tomcat**, with automated tests, health-check verification and rollback.

The sample application is a small **Student Feedback Portal** (Maven WAR project).

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Features](#features)
3. [Architecture](#architecture)
4. [Tech Stack](#tech-stack)
5. [Project Structure](#project-structure)
6. [Setup](#setup)
7. [Build and Test Locally](#build-and-test-locally)
8. [Jenkins Pipeline](#jenkins-pipeline)
9. [Deployment and Verification](#deployment-and-verification)
10. [Rollback](#rollback)
11. [Git Branching Strategy](#git-branching-strategy)
12. [Screenshots](#screenshots)
13. [Risks and Mitigation](#risks-and-mitigation)
14. [Author](#author)

---

## Problem Statement

Manual delivery (build locally, copy the WAR, deploy to Tomcat by hand, check in a browser) is slow, inconsistent and easy to break. Typical failures are the wrong artifact deployed, skipped unit tests, environment mismatch, missing logs and no rollback plan.

This project replaces that process with a repeatable CI/CD pipeline that pulls code from Git, builds and tests with Maven, deploys to Tomcat, verifies application health and restores the previous version if something fails.

## Features

- Maven WAR project with JUnit unit tests
- Declarative Jenkins pipeline (`Jenkinsfile`) with 7 stages
- Tests act as a quality gate: a failing test stops the pipeline before deployment
- Automatic deployment of the WAR to Tomcat `webapps/`
- Post-deployment health check (`curl`, expects HTTP 200) with retries
- Automatic rollback to the previous WAR if deployment verification fails
- WAR archived in Jenkins as a build artifact
- Git branching strategy (`main`, `dev`, `feature/*`) with commit conventions

## Architecture

```
Developer
   |
   | git push
   v
Git Repository (main / dev / feature/*)
   |
   | webhook or poll
   v
Jenkins Pipeline
   |-- Stage 1: Checkout
   |-- Stage 2: Maven Clean + Compile
   |-- Stage 3: Unit Test
   |-- Stage 4: Package WAR
   |-- Stage 5: Archive Artifact
   |-- Stage 6: Deploy to Tomcat
   |-- Stage 7: Health Check (curl)
   v
Apache Tomcat Server
   |
   v
Application URL (browser / API check)
```

**Quality gates**

- Tests fail: pipeline stops, nothing is deployed
- WAR not generated: pipeline fails
- Health check fails: deploy marked failed and previous WAR is restored

## Tech Stack

| Tool | Version | Role | Port |
|------|---------|------|------|
| Linux | Kali Linux on WSL2 (Ubuntu works the same) | Server environment | - |
| OpenJDK | 21 (compiled for Java 17) | Compile and run | - |
| Apache Maven | 3.9.12 | Build, test, package | - |
| Git | 2.53 | Version control | - |
| Jenkins | Stable (`jenkins.war`) | CI/CD automation | 8080 |
| Apache Tomcat | 10.1.34 | Application server | 8081 |

## Project Structure

```
devops-ci-cd-project/
├── src/
│   ├── main/
│   │   ├── java/com/internship/feedback/
│   │   │   ├── FeedbackService.java
│   │   │   ├── FeedbackServlet.java
│   │   │   └── HealthServlet.java
│   │   └── webapp/
│   │       └── index.jsp
│   └── test/java/com/internship/feedback/
│       └── FeedbackServiceTest.java
├── pom.xml
├── Jenkinsfile
├── docs/
│   ├── DevOps_CICD_Project_Solution.pdf
│   └── screenshots/
└── README.md
```

## Setup

### Prerequisites

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven git curl

java -version
mvn -version
git --version
```

### Tomcat 10.1.34 (port 8081)

```bash
cd ~
curl -O https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.34/bin/apache-tomcat-10.1.34.tar.gz
sudo mkdir -p /opt/tomcat
sudo tar xzf apache-tomcat-10.1.34.tar.gz -C /opt/tomcat --strip-components=1

# move Tomcat from 8080 to 8081 so it does not clash with Jenkins
sudo sed -i 's/port="8080"/port="8081"/' /opt/tomcat/conf/server.xml

# let the Jenkins user write WARs into webapps/
sudo chown -R $USER:$USER /opt/tomcat
sudo chmod +x /opt/tomcat/bin/*.sh

/opt/tomcat/bin/startup.sh
```

### Jenkins (port 8080)

```bash
sudo mkdir -p /opt/jenkins && cd /opt/jenkins
sudo curl -L -o jenkins.war https://get.jenkins.io/war-stable/latest/jenkins.war
nohup java -jar /opt/jenkins/jenkins.war --httpPort=8080 > ~/jenkins.log 2>&1 &
```

Open `http://localhost:8080`, unlock with the key from `~/.jenkins/secrets/initialAdminPassword`, install the suggested plugins and create the admin user.

### Verify the services

```bash
ps -ef | grep -E 'jenkins|catalina' | grep -v grep
ss -tulpn | grep -E '8080|8081'
```

## Build and Test Locally

```bash
git clone https://github.com/<your-username>/devops-ci-cd-project.git
cd devops-ci-cd-project

mvn clean
mvn test
mvn package

ls -lh target/app.war
```

A successful build ends with `BUILD SUCCESS` and produces `target/app.war`.

## Jenkins Pipeline

Create a **Pipeline** job:

1. Dashboard -> **New Item** -> name it (for example `devops-ci-cd-pipeline`) -> **Pipeline**
2. Build Triggers: enable **Poll SCM** with `H/5 * * * *` (or a GitHub webhook)
3. Pipeline: **Pipeline script from SCM**, SCM = **Git**
4. Repository URL: your GitHub repo (add credentials in Jenkins for private repos; never hardcode secrets)
5. Branch: `*/dev` (or `*/main`), Script Path: `Jenkinsfile`
6. Save and press **Build Now**

| # | Stage | Command | On failure |
|---|-------|---------|------------|
| 1 | Checkout | `checkout scm` | Pipeline stops |
| 2 | Build | `mvn -B clean compile` | Pipeline stops |
| 3 | Test | `mvn -B test` | Pipeline stops, no deploy |
| 4 | Package | `mvn -B package` | Pipeline fails |
| 5 | Archive | `archiveArtifacts target/*.war` | Pipeline stops |
| 6 | Deploy | back up old WAR, copy new WAR to `/opt/tomcat/webapps` | Rollback runs |
| 7 | Verify | `curl` health endpoint, expect HTTP 200 | Rollback runs |

The full pipeline definition is in the [`Jenkinsfile`](Jenkinsfile).

## Deployment and Verification

Jenkins copies `target/app.war` to `/opt/tomcat/webapps/`; Tomcat auto-deploys it.

| URL | Method | Purpose |
|-----|--------|---------|
| `http://localhost:8081/app/` | GET | Home page with the feedback form |
| `http://localhost:8081/app/submit` | POST | Validate and store feedback, show confirmation |
| `http://localhost:8081/app/health` | GET | Health check, returns `OK` |

```bash
curl -i http://localhost:8081/app/health

curl -i -X POST \
  -d "name=Asha&email=asha@example.com&feedback=great internship project" \
  http://localhost:8081/app/submit

tail -n 50 /opt/tomcat/logs/catalina.out
```

## Rollback

| Case | Scenario | Recovery |
|------|----------|----------|
| A | A unit test fails | Pipeline stops before deploy; fix the test or code and push again |
| B | New WAR does not pass the health check | Pipeline restores the previous WAR from `~/war-backups` automatically |
| C | A bad commit reached `dev` | `git revert <sha>`, push, the pipeline redeploys the good version |

Manual WAR restore:

```bash
ls -t ~/war-backups/
cp ~/war-backups/app-before-build-<N>.war /opt/tomcat/webapps/app.war
curl -i http://localhost:8081/app/health
```

Git rollback:

```bash
git log --oneline -5
git revert <bad-commit-sha>
git push origin dev
```

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable, deployable code (tagged releases such as `v1.0.0`) |
| `dev` | Integration branch |
| `feature/<name>` | Short-lived feature work, merged into `dev` |

Commit message convention:

```
feat: add feedback form page
fix: correct war packaging in pom
ci: update Jenkins deploy stage
test: add FeedbackService unit tests
docs: add rollback runbook
```

## Screenshots

**Tool versions and Tomcat setup**

![Tool versions](docs/screenshots/01-versions.png)

**Tomcat 10.1.34 running**

![Tomcat running](docs/screenshots/02-services.png)

**Jenkins started on port 8080**

![Jenkins start](docs/screenshots/03-jenkins-start.png)

![Jenkins command](docs/screenshots/03b-jenkins-cmd.png)

**Jenkins dashboard**

![Jenkins dashboard](docs/screenshots/05-jenkins-job.png)

![Jenkins dashboard 2](docs/screenshots/06-jenkins-dashboard.png)

![Jenkins welcome](docs/screenshots/07-jenkins-welcome.png)

**Maven build success (`target/app.war`)**

![Maven build](docs/screenshots/04-mvn-package.png)

**Health check**

![Health check](docs/screenshots/10-health.png)

**Form submission**

![Form submission](docs/screenshots/09-tomcat-app.png)

## Risks and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Port conflict (Jenkins vs Tomcat) | High | Tomcat runs on 8081 |
| Flaky Maven dependencies | Medium | Stable versions and a first-time local build |
| Permission errors on deploy | Medium | Tomcat directory owned by the Jenkins user |
| Pipeline succeeds but app broken | High | Mandatory health-check stage |
| Lost previous good build | Medium | Archived artifacts, WAR backups and Git tags |

## Author

**Ayushi**
DevOps Internship Project
