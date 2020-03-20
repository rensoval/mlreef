@echo off

echo ### STOP local docker context
docker-compose down
IF /I "%1"=="force" (
    echo.
    echo ### CLEANING local docker context
    docker-compose rm -f -v -s
    docker volume rm frontend_sock
    docker volume rm frontend_gitlab-data
    docker volume rm frontend_gitlab-runner-config
    docker volume rm frontend_gitlab-runner-data
    docker volume rm frontend_mlreefsql-data
    docker volume rm frontend_postgresql-data
    echo.
    docker volume ls
    docker volume prune -f
    echo Tip: You CAN delete other unused volumes with: docker volume prune
    echo.
    echo.
    echo # Recreating local.env file
    IF  "%GITLAB_SECRETS_DB_KEY_BASE%"=="" (
       GITLAB_SECRETS_DB_KEY_BASE=secret11111111112222222222333333333344444444445555555555666666666612345
    )
    IF  "%GITLAB_SECRETS_SECRET_KEY_BASE%"=="" (
       GITLAB_SECRETS_SECRET_KEY_BASE=secret11111111112222222222333333333344444444445555555555666666666612345
    )
    IF  "%GITLAB_SECRETS_OTP_KEY_BASE%"=="" (
       GITLAB_SECRETS_OTP_KEY_BASE=secret11111111112222222222333333333344444444445555555555666666666612345
    )
    IF  "%GITLAB_ADMIN_TOKEN%"=="" (
       GITLAB_ADMIN_TOKEN=QVj_FkeHyuJURko2ggZT
    )
    echo.
    echo # WRITING to local.env
    echo # generated by setup-local-environment.bat > local.env
    echo GITLAB_SECRETS_SECRET_KEY_BASE=%GITLAB_SECRETS_SECRET_KEY_BASE% >> local.env
    echo GITLAB_SECRETS_OTP_KEY_BASE=%GITLAB_SECRETS_OTP_KEY_BASE% >> local.env
    echo GITLAB_SECRETS_DB_KEY_BASE=%GITLAB_SECRETS_DB_KEY_BASE% >> local.env
    echo GITLAB_ADMIN_TOKEN=%GITLAB_ADMIN_TOKEN% >> local.env
)

docker-compose pull

IF /I "%1"=="" (
    echo Attention: run this script with argument "force" for the first time to generate local.env
)

echo.
echo ### 2. Manual Steps: register runners
echo This needs of lot of time (approx. 3-5 minutes) and must not be disturbed by SQL
echo Start initial setup
docker-compose up --detach gitlab
timeout 60
FOR /L %%G IN (1,1,7) DO (
timeout 10
echo ... wait for "http://localhost:10080/admin/runners"
curl -f -X GET  "http://localhost:10080/admin/runners"
)


echo.
REM # 1. Manual Steps
echo ### Please perform the manual steps for setup:
echo Login with root:password into your local gitlab instance
echo Gitlab will need some time to start (try refreshing in your browser)
echo.
echo  1. go to url: http://localhost:10080/admin/runners and copy the runner-registration-token
echo    Paste the runner-registration-token here:

set /p token="Paste the runner-registration-token here:"

echo token is saved in last-runner-registration-token.txt ...
echo REGISTER_RUNNER_TOKEN=%token% > last-runner-registration-token.txt

docker-compose up --detach gitlab-runner-dispatcher
docker exec gitlab-runner-dispatcher rm      /etc/gitlab-runner/config.toml
docker exec gitlab-runner-dispatcher gitlab-runner register --non-interactive  --url="http://gitlab:80/" --docker-network-mode mlreef-docker-network --registration-token "%token%" --executor "docker" --docker-image alpine:latest --docker-volumes /var/run/docker.sock:/var/run/docker.sock --description "local developer runner" --tag-list "docker" --run-untagged="true" --env "ENVIRONMENT_TEST_VARIABLE=foo-bar" --locked="false" --access-level="not_protected"

echo.
echo ### 3. Inject Admin token into gitlab
echo #
echo # If this fails: dont worry, setup gitlab manually and run those commands relaxed afters
echo #
echo Creating the admin token with GITLAB_ADMIN_TOKEN: %GITLAB_ADMIN_TOKEN%
docker exec -it postgresql bash -c "chmod +x /usr/local/bin/setup-gitlab.sh"
docker exec -it postgresql setup-gitlab.sh


echo ### 4. Restart local services in right order
echo Restarting services after initial setup
docker-compose up --detach gitlab
timeout 15
echo Let backend wait for gitlab restart ...
timeout 15
docker-compose stop backend nginx-proxy frontend
timeout 15
docker-compose up --detach
timeout 15
docker-compose stop backend
timeout 15
docker-compose up --detach

echo.
echo Test connection for admin:
curl -f -I -X GET --header "Content-Type: application/json" --header "Accept: application/json" --header "PRIVATE-TOKEN: %GITLAB_ADMIN_TOKEN%" "localhost:20080/api/v1"
curl -f -I -X GET --header "Content-Type: application/json" --header "Accept: application/json" --header "PRIVATE-TOKEN: %GITLAB_ADMIN_TOKEN%" "localhost:10080/api/v4/users/1"
