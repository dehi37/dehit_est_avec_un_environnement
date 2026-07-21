# TP4 – Automatisation CI/CD, GitOps & Sécurité de Spring PetClinic sur AWS

Ce projet s'inscrit dans la continuité du TP3. L'objectif de ce TP4 est d'industrialiser l'infrastructure de l'application Spring PetClinic sur AWS en mettant en place une approche GitOps rigoureuse, sécurisée (WAF, CDN CloudFront, OIDC, scans Trivy/tfsec), et entièrement automatisée via GitHub Actions.

## Architecture déployée

Internet ──> CloudFront (CDN) ──> AWS WAF (Sécurité) ──> ALB ──> ECS Fargate (Privé, 3 AZ) ──> RDS PostgreSQL (Privé)
└──> Backend Distant (S3 + DynamoDB Lock)


| Composant | Service AWS / Outils | Pilier Well-Architected |
|-----------|----------------------|-------------------------|
| **Réseau** | VPC + Subnets + NAT GW | Sécurité |
| **Sécurité Périmétrique** | AWS WAF (Règles SQLi & OWASP Top 10) | Sécurité |
| **Accélération & HTTPS** | Amazon CloudFront (CDN) | Performance / Sécurité |
| **Entrée Application** | ALB | Fiabilité / Sécurité |
| **Calcul / Conteneurs** | ECS Fargate (Serverless) | Performance / Fiabilité |
| **Scalabilité** | Application Auto Scaling | Performance |
| **Base de données** | RDS PostgreSQL Multi-AZ | Fiabilité |
| **Secrets** | AWS Secrets Manager | Sécurité |
| **IAM & Pipeline** | Rôles moindre privilège (OIDC sans clé) | Sécurité |
| **Registre d'images** | Amazon ECR | Excellence opérationnelle |
| **État Terraform** | S3 (Chiffré/Versionné) + DynamoDB (Lock) | Excellence opérationnelle |
| **Observabilité** | CloudWatch + SNS | Excellence opérationnelle |
| **Coûts** | AWS Budgets | Optimisation des coûts |

---

## Structure du Projet (Arborescence)

Le dépôt est structuré de manière à séparer strictement la création des ressources du backend d'état, l'infrastructure globale et le code applicatif.

```text
Projet_binome/
├── terraform/                                  # Dossier parent de l'infrastructure Cloud
│   ├── main.tf                                 # Orchestration de tous les modules (y compris WAF et CloudFront)
│   ├── variables.tf                            # Variables globales de l'infrastructure
│   ├── outputs.tf                              # Sorties (URL CloudFront, DNS de l'ALB, ARN, etc.)
│   ├── versions.tf                             # Déclarations des providers et des tags automatiques globaux
│   ├── backend.tf                              # [NOUVEAU - TP4] Configuration de l'état distant S3 + Lock DynamoDB
│   ├── terraform.tfvars                        # [NOUVEAU - TP4] Vos paramètres actifs (ex: binome_name, ID de compte)
│   ├── terraform.tfvars.example                # Modèle de configuration vierge pour le dépôt
│   ├── bootstrap/                              # [DÉPLACÉ - TP4] Initialisation de l'état distant
│   │   ├── main.tf                             # Création physique du Bucket S3 d'état et du verrou DynamoDB sur AWS
│   │   ├── variables.tf                        # Variables dédiées au bootstrapping du backend
│   │   └── outputs.tf                          # Sorties du bootstrap (ARN du bucket, nom de la table, etc.)
│   └── modules/                                # Dossier regroupant les sous-systèmes d'infrastructure
│       ├── vpc/                                # VPC, subnets publics/privés, NAT GW, tables de routage
│       ├── security_groups/                    # Pare-feu réseau (SG CloudFront -> SG ALB -> SG ECS -> SG RDS)
│       ├── ecr/                                # Registre d'images Docker privé pour l'application
│       ├── iam/                                # Rôles d'exécution ECS et tâches (moindre privilège)
│       ├── secrets/                            # Génération et stockage sécurisé du mot de passe RDS dans Secrets Manager
│       ├── rds/                                # Base de données PostgreSQL chiffrée en Multi-AZ
│       ├── alb/                                # Application Load Balancer
│       ├── ecs/                                # Fargate + Auto Scaling applicatif + Sonde Actuator
│       ├── cloudwatch/                         # Journalisation des logs, métriques et alarmes de performance
│       ├── budgets/                            # Gestion et alertes des coûts AWS mensuels
│       ├── waf/                                # [NOUVEAU - TP4] Pare-feu applicatif (Web Application Firewall) anti-exploits
│       └── cloudfront/                         # [NOUVEAU - TP4] CDN pour la mise en cache globale et le chiffrement de bout en bout
│
└── spring-petclinic/                           # Dossier contenant le code source de l'application Java
    ├── .github/                                # Dossier de configuration GitHub de l'application
    │   └── workflows/                          # Les 3 pipelines automatisés de livraison continue (CI/CD)
    │       ├── bootstrap.yml                # Création automatisée ou manuelle du socle S3/DynamoDB
    │       ├── infrastructure.yml           # Validation, scan tfsec et déploiement GitOps de l'IaC
    │       └── application.yml              # Test Maven, scan Trivy, push Docker, mise à jour ECS et Smoke test
    ├── src/                                    # Code source applicatif Java (Spring Boot)
    ├── pom.xml                                 # Fichier de configuration Maven (dépendances et build)
    ├── Dockerfile                              # Fichier de build multi-étapes pour générer l'image d'exécution légère
    └── settings.gradle                         # Fichier de configuration Gradle
---
```
## Pré-requis

- Un compte AWS opérationnel.
- Un rôle IAM nommé `petclinic-ci` configuré avec une relation de confiance **OIDC GitHub** (permettant une authentification sécurisée sans clé statique `AWS_ACCESS_KEY_ID`).
- GitHub CLI ou Git installé localement.

---

## Déploiement 100% Automatisé (CI/CD GitOps)

Plus aucun déploiement n'est effectué manuellement depuis votre machine locale. Tout passe par l'automatisation GitOps.

### Étape 1 : Initialisation du Backend Distant (À faire une seule fois)
Avant de déployer l'infrastructure, nous devons créer le Bucket S3 et la table DynamoDB qui stockeront de manière sécurisée l'état de Terraform.
1. Poussez votre projet sur votre dépôt GitHub.
2. Rendez-vous sur l'onglet **Actions** de votre dépôt GitHub.
3. Sélectionnez le workflow **"01 - AWS Backend Bootstrap"**.
4. Cliquez sur **Run workflow** pour lancer la création automatique.

### Étape 2 : Cycle GitOps de l'Infrastructure
Le pipeline **"02 - Infrastructure GitOps"** s'exécute automatiquement lors de modifications dans le dossier `terraform/` :
* **Sur une Pull Request :** L'IaC est formatée (`terraform fmt`), validée (`terraform validate`), auditée au niveau sécurité avec **tfsec**, et un plan d'exécution (`terraform plan`) est généré et commenté sur la PR.
* **Sur un Push (Merge) sur la branche `main` :** Le plan est appliqué (`terraform apply`) automatiquement sur AWS, créant l'ensemble de l'architecture.

### Étape 3 : Pipeline applicatif (CI/CD)
Lors de modifications apportées au code Java de l'application :
1. **Build & Tests unitaires :** Exécution complète des tests via Maven (`./mvnw clean verify`).
2. **Construction de l'image Docker :** Compilation multi-étapes optimisée. L'image est taguée avec le **SHA du commit Git** pour une traçabilité totale.
3. **Security Gate (Trivy) :** Scan complet de vulnérabilités (OS & bibliothèques applicatives) sur l'image Docker. Le pipeline échoue immédiatement si des failles de sévérité *HIGH* ou *CRITICAL* non corrigées sont détectées.
4. **Push sur Amazon ECR** de l'image validée.
5. **Déploiement progressif (Rolling Update) sur Amazon ECS Fargate**.
6. **Smoke Test & Rollback automatique :** Le pipeline attend 90 secondes puis effectue un test de santé sur l'URL de l'application via l'endpoint `/actuator/health`. En cas d'échec (statut non sain), le pipeline **rebascule automatiquement** (rollback) le service ECS Fargate vers la dernière version stable connue.

---

## Réponses aux questions de défense (TP4)

**Q1 : Pourquoi utiliser un Backend distant (S3 + DynamoDB) au lieu de conserver l'état en local ?**
→ L'état local interdit le travail en équipe (risques de conflits ou d'écrasement de ressources). Le stockage distant sur S3 centralise l'état, tandis que DynamoDB verrouille l'écriture (Locking). Si deux développeurs ou deux pipelines tentent d'appliquer des changements en même temps, le second est mis en attente pour éviter toute corruption d'infrastructure.

**Q2 : Quelle est la différence entre le S3 créé au TP3 et celui du TP4 ?**
→ Le bucket S3 du TP3 était applicatif et dédié au stockage des logs d'accès de l'ALB (audit de sécurité). Le bucket S3 du TP4 est purement technique et réservé à Terraform pour y enregistrer le fichier `terraform.tfstate`.

**Q3 : Comment le pipeline GitHub Actions s'authentifie-t-il sur AWS sans clé d'accès (Access Keys) ?**
→ Grâce au protocole **OIDC (OpenID Connect)**. GitHub Actions demande un jeton temporaire à AWS en assumant le rôle IAM `petclinic-ci`. Cela élimine totalement le besoin de stocker des clés AWS statiques à long terme dans les secrets GitHub, réduisant ainsi drastiquement les risques de fuites de credentials.

**Q4 : Comment est gérée la sécurité au niveau de notre conteneur applicatif ?**
→ À deux niveaux :
1. **À la construction (Dockerfile) :** Utilisation d'un processus multi-étapes. L'image finale d'exécution n'embarque pas Maven ou le JDK complet, mais seulement un JRE léger, ce qui réduit la surface d'attaque.
2. **Dans le pipeline (Trivy) :** L'analyseur Trivy scanne l'image pour détecter les vulnérabilités de dépendances connues avant de l'autoriser sur Amazon ECR.
