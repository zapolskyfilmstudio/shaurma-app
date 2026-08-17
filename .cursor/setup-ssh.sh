#!/usr/bin/env bash

if [ -z "${SSH_PRIVATE_KEY:-}" ] || [ -z "${SSH_HOST:-}" ] || [ -z "${SSH_USER:-}" ]; then
  echo "SSH secrets not available yet; skipping SSH setup (expected during environment build)."
  exit 0
fi

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

if ssh -o BatchMode=yes -o ConnectTimeout=15 shaurma-server "echo SSH OK && ls -la /root"; then
  echo "SSH connection verified."
else
  echo "SSH setup written, but connection test failed. Check SSH_PRIVATE_KEY and SSH_HOST secrets."
  exit 1
fi
