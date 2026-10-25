# Poetry demo

A small poem generator.

- `poetry-api/` Spring Boot 4 + Spring AI (Google Gemini), `POST /poems`
- `poetry-ui/` React + Vite + Bootstrap

Hosting on AWS:

| Part | AWS service |
| --- | --- |
| React UI | AWS Amplify Hosting (builds from GitHub on every push) |
| Spring Boot API | Docker image in ECR, run by Amazon ECS Express Mode (Fargate + load balancer, HTTPS URL provided for you) |
| Gemini API key | AWS Secrets Manager |
| API deployments | GitHub Actions (`.github/workflows/deploy-api.yml`) signing in to AWS with OIDC, so no AWS keys are stored in GitHub |

> AWS App Runner is closed to new customers (since 30 April 2026), so the API uses
> ECS Express Mode, which AWS recommends as the replacement.

## Run locally

API (JDK 25 or newer; compiles with `--release 25`):

    $env:GEMINI_API_KEY = "<your key>"
    cd poetry-api
    ./mvnw.cmd spring-boot:run

UI (in a second terminal):

    cd poetry-ui
    npm install
    npm run dev          # http://localhost:5173

Never commit your API key. Keep it in an IntelliJ environment variable locally and in
AWS Secrets Manager when deployed.

## Deploy to AWS, step by step

All commands are PowerShell. Do the parts in order: the UI address has to exist before the
API is created (the API needs it for CORS), and the API address has to exist before the
UI gets its final build.

### Part 1. One-off setup

1. Create an AWS account and sign in to the console as a user that can create IAM roles
   (the root user works for a demo, but create an admin user if you plan to keep the account).
2. Install the AWS CLI v2 (https://aws.amazon.com/cli/) and Docker Desktop.
3. Create an access key for your admin user (IAM > Users > your user > Security credentials
   > Create access key > "Command Line Interface (CLI)"), then run `aws configure` and paste
   the key, the secret, your region (this guide uses `eu-west-2`) and `json` for the output format.
4. Check it works. You should see your account number:

       aws sts get-caller-identity

5. Set variables you will reuse. Keep the same PowerShell window open (or paste these again):

       $REGION  = "eu-west-2"
       $ACCOUNT = (aws sts get-caller-identity --query Account --output text)
       $REPO    = "poetry-api"

   Pick a region where ECS Express Mode is available. If a command later says the
   feature is not available in your region, switch to a nearby major region.

### Part 2. Host the UI on Amplify

1. Push this repo to GitHub (it must contain `amplify.yml` in the repo root).
2. In the AWS console open **AWS Amplify > Create new app** (or **Deploy an app**), choose
   **GitHub**, and authorise AWS to read your repositories when asked.
3. Pick your repository and the branch `main`.
4. Tick **My app is a monorepo** and enter `poetry-ui` as the root directory.
5. On the build settings screen, Amplify should use the `amplify.yml` from the repo. If it
   shows its own detected settings instead, replace them with the contents of `amplify.yml`.
6. Leave environment variables empty for now (you add the API address in Part 4), then click
   **Save and deploy**.
7. Wait for the build to finish (all stages green). Amplify shows your UI address, which looks
   like `https://main.d1234abcd.amplifyapp.com`. Copy it:

       $UI_URL = "https://main.d1234abcd.amplifyapp.com"     # use your own address

   The page will load but poem generation will fail until Parts 3 and 4 are done.

### Part 3. Host the API (ECR + ECS Express Mode)

**3a. Create the image repository and push the image**

    aws ecr create-repository --repository-name $REPO --region $REGION
    aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"
    cd poetry-api
    docker build -t "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/${REPO}:v1" .
    docker push "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/${REPO}:v1"
    cd ..

**3b. Store the Gemini key in Secrets Manager**

    aws secretsmanager create-secret --name poetry/gemini-api-key --secret-string "<your key>" --region $REGION

Copy the `ARN` from the output into a variable:

    $SECRET_ARN = "arn:aws:secretsmanager:eu-west-2:123456789012:secret:poetry/gemini-api-key-AbCdEf"

**3c. Create the two IAM roles Express Mode needs**

Save these two files in the repo root (or anywhere you like):

`trust-tasks.json`

    {
      "Version": "2012-10-17",
      "Statement": [{ "Effect": "Allow", "Principal": { "Service": "ecs-tasks.amazonaws.com" }, "Action": "sts:AssumeRole" }]
    }

`trust-ecs.json`

    {
      "Version": "2012-10-17",
      "Statement": [{ "Effect": "Allow", "Principal": { "Service": "ecs.amazonaws.com" }, "Action": "sts:AssumeRole" }]
    }

Then:

    aws iam create-role --role-name ecsTaskExecutionRole --assume-role-policy-document file://trust-tasks.json
    aws iam attach-role-policy --role-name ecsTaskExecutionRole --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

    aws iam create-role --role-name ecsInfrastructureRoleForExpressServices --assume-role-policy-document file://trust-ecs.json
    aws iam attach-role-policy --role-name ecsInfrastructureRoleForExpressServices --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSInfrastructureRoleforExpressGatewayServices

(If `ecsTaskExecutionRole` already exists in your account, skip its two commands.)

Let the execution role read the Gemini secret. Save as `secret-access.json`, replacing the ARN:

    {
      "Version": "2012-10-17",
      "Statement": [{ "Effect": "Allow", "Action": "secretsmanager:GetSecretValue", "Resource": "<your secret ARN>" }]
    }

    aws iam put-role-policy --role-name ecsTaskExecutionRole --policy-name read-gemini-secret --policy-document file://secret-access.json

**3d. Describe the container**

Save as `container.json`, replacing the placeholder values:

    {
      "image": "<ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/poetry-api:v1",
      "containerPort": 8080,
      "environment": [
        { "name": "ALLOWED_ORIGINS", "value": "<your UI_URL from Part 2>" }
      ],
      "secrets": [
        { "name": "GEMINI_API_KEY", "valueFrom": "<your secret ARN>" }
      ]
    }

`ALLOWED_ORIGINS` must be exactly the Amplify address, with `https://` and no trailing slash.

**3e. Create the service**

    aws ecs create-express-gateway-service `
      --service-name poetry-api `
      --execution-role-arn "arn:aws:iam::${ACCOUNT}:role/ecsTaskExecutionRole" `
      --infrastructure-role-arn "arn:aws:iam::${ACCOUNT}:role/ecsInfrastructureRoleForExpressServices" `
      --primary-container file://container.json `
      --cpu 1024 --memory 2048 `
      --health-check-path /actuator/health `
      --monitor-resources `
      --region $REGION

This watches the rollout and takes several minutes (it creates the cluster, load balancer and
tasks). The Spring Boot app needs roughly 1 vCPU and 2 GB to start reliably, hence those sizes.

When it finishes, in the console go to **ECS > Clusters > default > Services > poetry-api**
and copy the service's **ARN** and its **HTTPS endpoint URL**.

    $SERVICE_ARN = "<service ARN>"
    $API_URL     = "https://<endpoint from the console>"

Test it in a browser or with `curl "$API_URL/actuator/health"`. You should get `{"status":"UP"}`.

If you get "Unable to assume the service linked role" on the first run, run
`aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com` and try again.

### Part 4. Point the UI at the API

1. In the Amplify console open your app > **Hosting > Environment variables** (the exact menu
   name can differ slightly; look for "Environment variables" in the app settings).
2. Add a variable named `VITE_API_URL` with your API address (`$API_URL`, including `https://`).
3. Save, then trigger a new build (Amplify > your branch > **Redeploy this version**), because
   Vite bakes the value in at build time.
4. Open your `$UI_URL` and generate a poem.

From now on every push to `main` rebuilds the UI automatically.

### Part 5. Automatic API deploys with GitHub Actions

`deploy-api.yml` rebuilds the image and rolls out the new version whenever you push changes
under `poetry-api/`.

**5a. Let GitHub sign in to AWS (OIDC)**

1. AWS console > **IAM > Identity providers > Add provider**.
2. Provider type **OpenID Connect**, provider URL `https://token.actions.githubusercontent.com`,
   audience `sts.amazonaws.com`. Click **Add provider**. (Skip if it already exists.)
3. Create the role. Save as `trust-github.json`, replacing `<ACCOUNT>`, and change the repo
   name if yours differs:

       {
         "Version": "2012-10-17",
         "Statement": [{
           "Effect": "Allow",
           "Principal": { "Federated": "arn:aws:iam::<ACCOUNT>:oidc-provider/token.actions.githubusercontent.com" },
           "Action": "sts:AssumeRoleWithWebIdentity",
           "Condition": {
             "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
             "StringLike":   { "token.actions.githubusercontent.com:sub": "repo:PaddyMacMac/poetry-generator:ref:refs/heads/main" }
           }
         }]
       }

       aws iam create-role --role-name github-poetry-deploy --assume-role-policy-document file://trust-github.json

4. Give the role only what the workflow needs. Save as `deploy-permissions.json`, replacing
   `<ACCOUNT>` and `<REGION>`:

       {
         "Version": "2012-10-17",
         "Statement": [
           { "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
           { "Effect": "Allow",
             "Action": ["ecr:BatchCheckLayerAvailability","ecr:InitiateLayerUpload","ecr:UploadLayerPart","ecr:CompleteLayerUpload","ecr:PutImage","ecr:BatchGetImage","ecr:GetDownloadUrlForLayer"],
             "Resource": "arn:aws:ecr:<REGION>:<ACCOUNT>:repository/poetry-api" },
           { "Effect": "Allow", "Action": "ecs:UpdateExpressGatewayService", "Resource": "*" },
           { "Effect": "Allow", "Action": "iam:PassRole",
             "Resource": ["arn:aws:iam::<ACCOUNT>:role/ecsTaskExecutionRole","arn:aws:iam::<ACCOUNT>:role/ecsInfrastructureRoleForExpressServices"] }
         ]
       }

       aws iam put-role-policy --role-name github-poetry-deploy --policy-name deploy --policy-document file://deploy-permissions.json

   If the workflow later fails with `AccessDenied`, the error message names the missing
   permission. Add it to this policy and re-run the job.

**5b. Add the settings to GitHub**

In your repository on github.com go to **Settings > Secrets and variables > Actions**.

On the **Secrets** tab, **New repository secret**:

| Name | Value |
| --- | --- |
| `AWS_ROLE_ARN` | `arn:aws:iam::<ACCOUNT>:role/github-poetry-deploy` |

On the **Variables** tab, **New repository variable**:

| Name | Value |
| --- | --- |
| `AWS_REGION` | `eu-north-1` |
| `ECR_REPOSITORY` | `poetry-api` |
| `ECS_SERVICE_ARN` | the service ARN from Part 3e |

**5c. Run it**

Commit and push to `main`, then open the repo's **Actions** tab. To run the workflow without
changing code, pick it in the left list and click **Run workflow**.

Check after the first API deployment that the new tasks still have the `GEMINI_API_KEY`
secret and `ALLOWED_ORIGINS` setting (ECS > the service > the latest revision). If a deploy
ever drops them, run
`aws ecs update-express-gateway-service --service-arn $SERVICE_ARN --primary-container file://container.json`
with the full `container.json` (updating its `image` to the newest tag).

### Clean up (to stop charges)

Express Mode runs a load balancer and Fargate tasks around the clock, so delete them when
you are finished experimenting:

    aws ecs delete-express-gateway-service --service-arn $SERVICE_ARN --region $REGION

Then delete the Amplify app (Amplify console > your app > App settings > General settings >
Delete app), delete the ECR repository, and delete the Secrets Manager secret.
