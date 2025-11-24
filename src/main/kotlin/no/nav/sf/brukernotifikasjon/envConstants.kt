package no.nav.sf.brukernotifikasjon

/**
 * Naming convention applied to environment variable constants: a lowercase prefix separated from the actual constant, i.e. prefix_ENVIRONMENT_VARIABLE_NAME.
 *
 * Motivation:
 * The prefix provides contextual naming that describes the source and nature of the variables they represent while keeping the names short.
 * A prefix marks a constant representing an environment variable, and also where one can find the value of that variable
 *
 * - env: Denotes an environment variable typically injected into the pod by the Nais platform.
 *
 * - config: Denotes an environment variable explicitly configured in YAML files (see dev.yaml, prod.yaml)
 *
 * - secret: Denotes an environment variable loaded from a Kubernetes secret.
 */
const val config_TMS_VARSEL_TOPIC = "TMS_VARSEL_TOPIC"
const val config_TMS_INAKTIVER_TOPIC = "TMS_INAKTIVER_TOPIC"
const val config_KAFKA_TOPIC_DONE = "KAFKA_TOPIC_DONE" // TODO DEPRECATED
const val config_KAFKA_TOPIC_INNBOKS = "KAFKA_TOPIC_INNBOKS" // TODO DEPRECATED
const val config_CONTEXT = "CONTEXT"

const val env_NAIS_NAMESPACE = "NAIS_NAMESPACE"
const val env_NAIS_APP_NAME = "NAIS_APP_NAME"
const val env_KAFKA_SCHEMA_REGISTRY = "KAFKA_SCHEMA_REGISTRY"
const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

// Kafka injected environment dependencies
const val env_KAFKA_BROKERS = "KAFKA_BROKERS"
const val env_KAFKA_KEYSTORE_PATH = "KAFKA_KEYSTORE_PATH"
const val env_KAFKA_CREDSTORE_PASSWORD = "KAFKA_CREDSTORE_PASSWORD"
const val env_KAFKA_TRUSTSTORE_PATH = "KAFKA_TRUSTSTORE_PATH"
const val env_KAFKA_SCHEMA_REGISTRY_USER = "KAFKA_SCHEMA_REGISTRY_USER"
const val env_KAFKA_SCHEMA_REGISTRY_PASSWORD = "KAFKA_SCHEMA_REGISTRY_PASSWORD"

/**
 * Shortcut for fetching environment variables
 */
fun env(name: String): String = System.getenv(name) ?: throw NullPointerException("Missing env $name")
