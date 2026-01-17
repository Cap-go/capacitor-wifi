import { WebPlugin } from '@capacitor/core';

import type {
  AddNetworkOptions,
  CapacitorWifiPlugin,
  ConnectOptions,
  DisconnectOptions,
  GetAvailableNetworksResult,
  GetIpAddressResult,
  GetRssiResult,
  GetSsidResult,
  IsEnabledResult,
  PermissionStatus,
  RequestPermissionsOptions,
  WifiInfo,
} from './definitions';

export class CapacitorWifiWeb extends WebPlugin implements CapacitorWifiPlugin {
  async addNetwork(_options: AddNetworkOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async connect(_options: ConnectOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async disconnect(_options?: DisconnectOptions): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getAvailableNetworks(): Promise<GetAvailableNetworksResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getIpAddress(): Promise<GetIpAddressResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getRssi(): Promise<GetRssiResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getSsid(): Promise<GetSsidResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getWifiInfo(): Promise<WifiInfo> {
    throw this.unimplemented('Not implemented on web.');
  }

  async isEnabled(): Promise<IsEnabledResult> {
    throw this.unimplemented('Not implemented on web.');
  }

  async startScan(): Promise<void> {
    throw this.unimplemented('Not implemented on web.');
  }

  async checkPermissions(): Promise<PermissionStatus> {
    throw this.unimplemented('Not implemented on web.');
  }

  async requestPermissions(_options?: RequestPermissionsOptions): Promise<PermissionStatus> {
    throw this.unimplemented('Not implemented on web.');
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: '1.0.0' };
  }
}
