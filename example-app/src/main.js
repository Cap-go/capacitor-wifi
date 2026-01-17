
import './style.css';
import { CapacitorWifi } from '@capgo/capacitor-wifi';

const plugin = CapacitorWifi;
const state = {};


const actions = [
{
              id: 'get-plugin-version',
              label: 'Get plugin version',
              description: 'Calls getPluginVersion() to get the native plugin version.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getPluginVersion();
return result;
              },
            },
{
              id: 'check-permissions',
              label: 'Check permissions',
              description: 'Checks the current location permission status (required for WiFi operations).',
              inputs: [],
              run: async (values) => {
                const result = await plugin.checkPermissions();
return result;
              },
            },
{
              id: 'request-permissions',
              label: 'Request permissions',
              description: 'Requests location permissions from the user (required for WiFi operations).',
              inputs: [],
              run: async (values) => {
                const result = await plugin.requestPermissions();
return result;
              },
            },
{
              id: 'get-ssid',
              label: 'Get current SSID',
              description: 'Gets the SSID of the currently connected WiFi network.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getSsid();
return result;
              },
            },
{
              id: 'get-ip-address',
              label: 'Get IP address',
              description: 'Gets the device\'s current IP address.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getIpAddress();
return result;
              },
            },
{
              id: 'get-rssi',
              label: 'Get RSSI (Android)',
              description: 'Gets the received signal strength indicator (RSSI) of the current network in dBm. Android only.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getRssi();
return result;
              },
            },
{
              id: 'get-wifi-info',
              label: 'Get WiFi info',
              description: 'Gets comprehensive information about the currently connected WiFi network including SSID, BSSID, IP, frequency, link speed, and signal strength in a single call.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getWifiInfo();
return result;
              },
            },
{
              id: 'is-enabled',
              label: 'Check if WiFi enabled (Android)',
              description: 'Checks if WiFi is enabled on the device. Android only.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.isEnabled();
return result;
              },
            },
{
              id: 'start-scan',
              label: 'Start network scan (Android)',
              description: 'Starts scanning for available WiFi networks. Android only. Results delivered via networksScanned event.',
              inputs: [],
              run: async (values) => {
                await plugin.startScan();
return 'Scan started. Listen for networksScanned event.';
              },
            },
{
              id: 'get-available-networks',
              label: 'Get available networks (Android)',
              description: 'Gets the list of available WiFi networks from the last scan. Android only.',
              inputs: [],
              run: async (values) => {
                const result = await plugin.getAvailableNetworks();
return result;
              },
            },
{
              id: 'connect',
              label: 'Connect to network',
              description: 'Connects to a WiFi network. Creates temporary connection on Android, persistent on iOS.',
              inputs: [{ name: 'ssid', label: 'SSID', type: 'text', value: '' }, { name: 'password', label: 'Password (optional)', type: 'text', value: '' }, { name: 'isHiddenSsid', label: 'Hidden network (Android)', type: 'checkbox', value: false }, { name: 'autoRouteTraffic', label: 'Auto-route traffic (Android)', type: 'checkbox', value: false }],
              run: async (values) => {
                const options = { ssid: values.ssid };
if (values.password) options.password = values.password;
if (values.isHiddenSsid) options.isHiddenSsid = true;
if (values.autoRouteTraffic) options.autoRouteTraffic = true;
await plugin.connect(options);
return 'Connected successfully';
              },
            },
{
              id: 'add-network',
              label: 'Add network',
              description: 'Shows system dialog to add a WiFi network (Android SDK 30+) or connects directly (iOS).',
              inputs: [{ name: 'ssid', label: 'SSID', type: 'text', value: '' }, { name: 'password', label: 'Password (optional)', type: 'text', value: '' }, { name: 'isHiddenSsid', label: 'Hidden network (Android)', type: 'checkbox', value: false }],
              run: async (values) => {
                const options = { ssid: values.ssid };
if (values.password) options.password = values.password;
if (values.isHiddenSsid) options.isHiddenSsid = true;
await plugin.addNetwork(options);
return 'Network added successfully';
              },
            },
{
              id: 'disconnect',
              label: 'Disconnect from network',
              description: 'Disconnects from the current WiFi network. On iOS, only disconnects networks added via this plugin.',
              inputs: [{ name: 'ssid', label: 'SSID (optional)', type: 'text', value: '' }],
              run: async (values) => {
                const options = values.ssid ? { ssid: values.ssid } : undefined;
await plugin.disconnect(options);
return 'Disconnected successfully';
              },
            }
];

const actionSelect = document.getElementById('action-select');
const formContainer = document.getElementById('action-form');
const descriptionBox = document.getElementById('action-description');
const runButton = document.getElementById('run-action');
const output = document.getElementById('plugin-output');

function buildForm(action) {
  formContainer.innerHTML = '';
  if (!action.inputs || !action.inputs.length) {
    const note = document.createElement('p');
    note.className = 'no-input-note';
    note.textContent = 'This action does not require any inputs.';
    formContainer.appendChild(note);
    return;
  }
  action.inputs.forEach((input) => {
    const fieldWrapper = document.createElement('div');
    fieldWrapper.className = input.type === 'checkbox' ? 'form-field inline' : 'form-field';

    const label = document.createElement('label');
    label.textContent = input.label;
    label.htmlFor = `field-${input.name}`;

    let field;
    switch (input.type) {
      case 'textarea': {
        field = document.createElement('textarea');
        field.rows = input.rows || 4;
        break;
      }
      case 'select': {
        field = document.createElement('select');
        (input.options || []).forEach((option) => {
          const opt = document.createElement('option');
          opt.value = option.value;
          opt.textContent = option.label;
          if (input.value !== undefined && option.value === input.value) {
            opt.selected = true;
          }
          field.appendChild(opt);
        });
        break;
      }
      case 'checkbox': {
        field = document.createElement('input');
        field.type = 'checkbox';
        field.checked = Boolean(input.value);
        break;
      }
      case 'number': {
        field = document.createElement('input');
        field.type = 'number';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
        break;
      }
      default: {
        field = document.createElement('input');
        field.type = 'text';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
      }
    }

    field.id = `field-${input.name}`;
    field.name = input.name;
    field.dataset.type = input.type || 'text';

    if (input.placeholder && input.type !== 'checkbox') {
      field.placeholder = input.placeholder;
    }

    if (input.type === 'checkbox') {
      fieldWrapper.appendChild(field);
      fieldWrapper.appendChild(label);
    } else {
      fieldWrapper.appendChild(label);
      fieldWrapper.appendChild(field);
    }

    formContainer.appendChild(fieldWrapper);
  });
}

function getFormValues(action) {
  const values = {};
  (action.inputs || []).forEach((input) => {
    const field = document.getElementById(`field-${input.name}`);
    if (!field) return;
    switch (input.type) {
      case 'number': {
        values[input.name] = field.value === '' ? null : Number(field.value);
        break;
      }
      case 'checkbox': {
        values[input.name] = field.checked;
        break;
      }
      default: {
        values[input.name] = field.value;
      }
    }
  });
  return values;
}

function setAction(action) {
  descriptionBox.textContent = action.description || '';
  buildForm(action);
  output.textContent = 'Ready to run the selected action.';
}

function populateActions() {
  actionSelect.innerHTML = '';
  actions.forEach((action) => {
    const option = document.createElement('option');
    option.value = action.id;
    option.textContent = action.label;
    actionSelect.appendChild(option);
  });
  setAction(actions[0]);
}

actionSelect.addEventListener('change', () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (action) {
    setAction(action);
  }
});

runButton.addEventListener('click', async () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (!action) return;
  const values = getFormValues(action);
  try {
    const result = await action.run(values);
    if (result === undefined) {
      output.textContent = 'Action completed.';
    } else if (typeof result === 'string') {
      output.textContent = result;
    } else {
      output.textContent = JSON.stringify(result, null, 2);
    }
  } catch (error) {
    output.textContent = `Error: ${error?.message ?? error}`;
  }
});

populateActions();

// Navigation between pages
const navButtons = document.querySelectorAll('.nav-button');
const pageViews = document.querySelectorAll('.page-view');

navButtons.forEach(button => {
  button.addEventListener('click', () => {
    const targetPage = button.dataset.page;
    
    // Update active button
    navButtons.forEach(btn => btn.classList.remove('active'));
    button.classList.add('active');
    
    // Show/hide pages
    pageViews.forEach(view => view.classList.remove('active'));
    document.getElementById(`${targetPage}-page`).classList.add('active');
  });
});
