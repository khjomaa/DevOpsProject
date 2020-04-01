#!/usr/bin/env bash

echo "### Install minikube ###"
minikube start --cpus 2 --memory 6144 --vm-driver virtualbox

echo "" && echo "### Install Jenkins ###"
kubectl apply -f jenkins/namespace.yaml
kubectl apply -f jenkins/clusterrolebinding.yaml
helm upgrade --install --wait jenkins stable/jenkins -f jenkins/my-values.yaml -n jenkins
kubectl -n jenkins port-forward svc/jenkins 8080:8080 &
