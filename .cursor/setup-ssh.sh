#!/usr/bin/env bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
printf '%s\n' "$SSH_PRIVATE_KEY" > ~/.ssh/id_ed25519
chmod 600 ~/.ssh/id_ed25519
{
  echo "Host shaurma-server"
  echo "  HostName ${SSH_HOST}"
  echo "  User ${SSH_USER}"
  echo "  IdentityFile ~/.ssh/id_ed25519"
  echo "  StrictHostKeyChecking accept-new"
} > ~/.ssh/config
chmod 600 ~/.ssh/config
ssh -o BatchMode=yes -o ConnectTimeout=15 shaurma-server "echo SSH OK && ls -la /root"
