{{/*
Expand the name of the chart.
*/}}
{{- define "anadolu-flight-system.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "anadolu-flight-system.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "anadolu-flight-system.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "anadolu-flight-system.labels" -}}
helm.sh/chart: {{ include "anadolu-flight-system.chart" . }}
{{ include "anadolu-flight-system.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: anadolu-flight-system
{{- end }}

{{/*
Selector labels
*/}}
{{- define "anadolu-flight-system.selectorLabels" -}}
app.kubernetes.io/name: {{ include "anadolu-flight-system.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Service specific labels
*/}}
{{- define "anadolu-flight-system.serviceLabels" -}}
{{- $serviceName := .serviceName -}}
app.kubernetes.io/name: {{ $serviceName }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: {{ .component | default "backend" }}
app.kubernetes.io/part-of: anadolu-flight-system
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "anadolu-flight-system.serviceAccountName" -}}
{{- if .Values.security.serviceAccount.create }}
{{- default (include "anadolu-flight-system.fullname" .) .Values.security.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.security.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Common environment variables for all services
*/}}
{{- define "anadolu-flight-system.commonEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.common.springProfile | quote }}
- name: JAVA_OPTS
  value: {{ .Values.common.javaOpts | quote }}
- name: KAFKA_BOOTSTRAP_SERVERS
  value: "{{ .Release.Name }}-kafka:9092"
- name: REDIS_HOST
  value: "{{ .Release.Name }}-redis-master"
- name: REDIS_PORT
  value: "6379"
- name: ZIPKIN_URL
  value: "http://zipkin:9411/api/v2/spans"
{{- end }}

{{/*
Liveness probe configuration
*/}}
{{- define "anadolu-flight-system.livenessProbe" -}}
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  initialDelaySeconds: {{ .Values.common.probes.liveness.initialDelaySeconds }}
  periodSeconds: {{ .Values.common.probes.liveness.periodSeconds }}
  timeoutSeconds: {{ .Values.common.probes.liveness.timeoutSeconds }}
  failureThreshold: {{ .Values.common.probes.liveness.failureThreshold }}
{{- end }}

{{/*
Readiness probe configuration
*/}}
{{- define "anadolu-flight-system.readinessProbe" -}}
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  initialDelaySeconds: {{ .Values.common.probes.readiness.initialDelaySeconds }}
  periodSeconds: {{ .Values.common.probes.readiness.periodSeconds }}
  timeoutSeconds: {{ .Values.common.probes.readiness.timeoutSeconds }}
  failureThreshold: {{ .Values.common.probes.readiness.failureThreshold }}
{{- end }}

{{/*
Startup probe configuration
*/}}
{{- define "anadolu-flight-system.startupProbe" -}}
startupProbe:
  httpGet:
    path: /actuator/health
    port: http
  initialDelaySeconds: {{ .Values.common.probes.startup.initialDelaySeconds }}
  periodSeconds: {{ .Values.common.probes.startup.periodSeconds }}
  timeoutSeconds: {{ .Values.common.probes.startup.timeoutSeconds }}
  failureThreshold: {{ .Values.common.probes.startup.failureThreshold }}
{{- end }}

{{/*
Pod security context
*/}}
{{- define "anadolu-flight-system.podSecurityContext" -}}
securityContext:
  runAsNonRoot: {{ .Values.security.podSecurityContext.runAsNonRoot }}
  runAsUser: {{ .Values.security.podSecurityContext.runAsUser }}
  fsGroup: {{ .Values.security.podSecurityContext.fsGroup }}
{{- end }}

{{/*
Container security context
*/}}
{{- define "anadolu-flight-system.containerSecurityContext" -}}
securityContext:
  allowPrivilegeEscalation: {{ .Values.security.containerSecurityContext.allowPrivilegeEscalation }}
  readOnlyRootFilesystem: {{ .Values.security.containerSecurityContext.readOnlyRootFilesystem }}
  capabilities:
    drop:
    {{- range .Values.security.containerSecurityContext.capabilities.drop }}
      - {{ . }}
    {{- end }}
{{- end }}
