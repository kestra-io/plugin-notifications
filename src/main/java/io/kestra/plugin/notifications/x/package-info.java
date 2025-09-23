/**
 * X (formerly Twitter) notification tasks for Kestra.
 * 
 * This package provides tasks for sending notifications to X (formerly Twitter) 
 * when Kestra flow executions are triggered. It supports both OAuth 2.0 Bearer 
 * token authentication and OAuth 1.0a authentication methods.
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Send tweets/posts with execution information</li>
 *   <li>Support for custom messages and fields</li>
 *   <li>Automatic status emoji based on execution status</li>
 *   <li>Character limit validation (280 characters)</li>
 *   <li>Media attachment support via public URLs</li>
 * </ul>
 * 
 * <h2>Authentication</h2>
 * <p>This plugin supports two authentication methods:</p>
 * <ul>
 *   <li><strong>OAuth 2.0 Bearer Token:</strong> Recommended for most use cases</li>
 *   <li><strong>OAuth 1.0a:</strong> For legacy applications requiring consumer key/secret and access token/secret</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * tasks:
 *   - id: post_on_x
 *     type: io.kestra.plugin.notifications.x.XExecution
 *     bearerToken: "{{ secret('X_API_BEARER_TOKEN') }}"
 *     customMessage: "⚠️ Flow failed: {{ flow.namespace }}.{{ flow.id }} #Kestra"
 * }</pre>
 * 
 * <h2>API Limits</h2>
 * <p>Please be aware of X API rate limits and usage policies:</p>
 * <ul>
 *   <li>Free tier: 500 post writes per month</li>
 *   <li>Character limit: 280 characters per tweet</li>
 *   <li>Rate limits apply based on your API tier</li>
 * </ul>
 * 
 * @see <a href="https://docs.x.com/x-api/introduction">X API Documentation</a>
 * @see <a href="https://kestra.io/docs/administrator-guide/monitoring#alerting">Kestra Alerting Documentation</a>
 */
package io.kestra.plugin.notifications.x;
