import { CapacitorHttp } from '@capacitor/core';

// Wait for DOM to be ready
function initHttpTest() {
  const ipAddressInput = document.getElementById('ip-address');
  const portInput = document.getElementById('port');
  const pathInput = document.getElementById('path');
  const methodSelect = document.getElementById('method');
  const makeRequestButton = document.getElementById('make-request');
  const responseOutput = document.getElementById('response-output');
  const statusIndicator = document.getElementById('status-indicator');

  if (!ipAddressInput || !portInput || !pathInput || !methodSelect || !makeRequestButton || !responseOutput || !statusIndicator) {
    // Elements not ready yet, try again later
    setTimeout(initHttpTest, 100);
    return;
  }

  function updateStatus(status, text) {
    statusIndicator.className = `status-indicator status-${status}`;
    statusIndicator.textContent = text;
  }

  function log(message) {
    const timestamp = new Date().toLocaleTimeString();
    responseOutput.textContent = `[${timestamp}] ${message}\n${responseOutput.textContent}`;
  }

  async function makeHttpRequest() {
    const ip = ipAddressInput.value.trim();
    const port = portInput.value.trim();
    const path = pathInput.value.trim() || '/';
    const method = methodSelect.value;

    if (!ip) {
      log('Error: IP address is required');
      updateStatus('error', 'Error');
      return;
    }

    if (!port || isNaN(parseInt(port)) || parseInt(port) < 1 || parseInt(port) > 65535) {
      log('Error: Valid port number (1-65535) is required');
      updateStatus('error', 'Error');
      return;
    }

    // Construct HTTP URL (not HTTPS)
    const url = `http://${ip}:${port}${path}`;
    
    log(`Making ${method} request to: ${url} (10s timeout)`);
    updateStatus('pending', 'Requesting...');
    makeRequestButton.disabled = true;

    try {
      const options = {
        url: url,
        method: method,
        headers: {
          'Content-Type': 'application/json',
        },
        connectTimeout: 10000, // 10 seconds
        readTimeout: 10000, // 10 seconds
      };

      const startTime = Date.now();
      
      // Create a timeout promise
      const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => {
          reject(new Error('Request timeout after 10 seconds'));
        }, 10000);
      });

      // Race between the request and timeout
      const response = await Promise.race([
        CapacitorHttp.request(options),
        timeoutPromise
      ]);
      
      const duration = Date.now() - startTime;

      // Log response details
      log(`\n=== Response Details ===`);
      log(`Status: ${response.status}`);
      log(`Duration: ${duration}ms`);
      log(`URL: ${response.url}`);
      log(`\n=== Headers ===`);
      Object.entries(response.headers).forEach(([key, value]) => {
        log(`${key}: ${value}`);
      });
      
      log(`\n=== Response Body (as text) ===`);
      
      // Try to get response as text
      let responseText;
      if (typeof response.data === 'string') {
        responseText = response.data;
      } else if (response.data instanceof ArrayBuffer) {
        responseText = new TextDecoder().decode(response.data);
      } else if (response.data instanceof Uint8Array) {
        responseText = new TextDecoder().decode(response.data);
      } else {
        // If it's an object, stringify it
        responseText = JSON.stringify(response.data, null, 2);
      }
      
      log(responseText);
      
      updateStatus('success', `Success (${response.status})`);
    } catch (error) {
      log(`\n=== Error ===`);
      if (error.message && error.message.includes('timeout')) {
        log(`Timeout: Request exceeded 10 second timeout`);
        log(`This may indicate the device is not reachable on the connected Wi-Fi network.`);
      } else {
        log(`Message: ${error.message || error}`);
        if (error.status) {
          log(`Status: ${error.status}`);
        }
        if (error.data) {
          let errorText;
          if (typeof error.data === 'string') {
            errorText = error.data;
          } else if (error.data instanceof ArrayBuffer) {
            errorText = new TextDecoder().decode(error.data);
          } else if (error.data instanceof Uint8Array) {
            errorText = new TextDecoder().decode(error.data);
          } else {
            errorText = JSON.stringify(error.data, null, 2);
          }
          log(`Response: ${errorText}`);
        }
      }
      updateStatus('error', 'Error');
    } finally {
      makeRequestButton.disabled = false;
    }
  }

  makeRequestButton.addEventListener('click', makeHttpRequest);

  // Allow Enter key to trigger request
  [ipAddressInput, portInput, pathInput].forEach(input => {
    input.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        makeHttpRequest();
      }
    });
  });

  log('HTTP Test page loaded. Use this to test if traffic routes through connected Wi-Fi network.');
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initHttpTest);
} else {
  initHttpTest();
}
