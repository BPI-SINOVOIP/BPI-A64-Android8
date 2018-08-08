/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import android.net.UidRange;

/** {@hide} */
interface INetd {
    /**
     * Returns true if the service is responding.
     */
    boolean isAlive();

    /**
     * Replaces the contents of the specified UID-based firewall chain.
     *
     * The chain may be a whitelist chain or a blacklist chain. A blacklist chain contains DROP
     * rules for the specified UIDs and a RETURN rule at the end. A whitelist chain contains RETURN
     * rules for the system UID range (0 to {@code UID_APP} - 1), RETURN rules for for the specified
     * UIDs, and a DROP rule at the end. The chain will be created if it does not exist.
     *
     * @param chainName The name of the chain to replace.
     * @param isWhitelist Whether this is a whitelist or blacklist chain.
     * @param uids The list of UIDs to allow/deny.
     * @return true if the chain was successfully replaced, false otherwise.
     */
    boolean firewallReplaceUidChain(String chainName, boolean isWhitelist, in int[] uids);

    /**
     * Enables or disables data saver mode on costly network interfaces.
     *
     * - When disabled, all packets to/from apps in the penalty box chain are rejected on costly
     *   interfaces. Traffic to/from other apps or on other network interfaces is allowed.
     * - When enabled, only apps that are in the happy box chain and not in the penalty box chain
     *   are allowed network connectivity on costly interfaces. All other packets on these
     *   interfaces are rejected. The happy box chain always contains all system UIDs; to disallow
     *   traffic from system UIDs, place them in the penalty box chain.
     *
     * By default, data saver mode is disabled. This command has no effect but might still return an
     * error) if {@code enable} is the same as the current value.
     *
     * @param enable whether to enable or disable data saver mode.
     * @return true if the if the operation was successful, false otherwise.
     */
    boolean bandwidthEnableDataSaver(boolean enable);

    /**
     * Adds or removes one rule for each supplied UID range to prohibit all network activity outside
     * of secure VPN.
     *
     * When a UID is covered by one of these rules, traffic sent through any socket that is not
     * protected or explicitly overriden by the system will be rejected. The kernel will respond
     * with an ICMP prohibit message.
     *
     * Initially, there are no such rules. Any rules that are added will only last until the next
     * restart of netd or the device.
     *
     * @param add {@code true} if the specified UID ranges should be denied access to any network
     *        which is not secure VPN by adding rules, {@code false} to remove existing rules.
     * @param uidRanges a set of non-overlapping, contiguous ranges of UIDs to which to apply or
     *        remove this restriction.
     *        <p> Added rules should not overlap with existing rules. Likewise, removed rules should
     *        each correspond to an existing rule.
     *
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void networkRejectNonSecureVpn(boolean add, in UidRange[] uidRanges);

    /**
     * Administratively closes sockets belonging to the specified UIDs.
     */
    void socketDestroy(in UidRange[] uidRanges, in int[] exemptUids);

    // Array indices for resolver parameters.
    const int RESOLVER_PARAMS_SAMPLE_VALIDITY = 0;
    const int RESOLVER_PARAMS_SUCCESS_THRESHOLD = 1;
    const int RESOLVER_PARAMS_MIN_SAMPLES = 2;
    const int RESOLVER_PARAMS_MAX_SAMPLES = 3;
    const int RESOLVER_PARAMS_COUNT = 4;

    /**
     * Sets the name servers, search domains and resolver params for the given network. Flushes the
     * cache as needed (i.e. when the servers or the number of samples to store changes).
     *
     * @param netId the network ID of the network for which information should be configured.
     * @param servers the DNS servers to configure for the network.
     * @param domains the search domains to configure.
     * @param params the params to set. This array contains RESOLVER_PARAMS_COUNT integers that
     *   encode the contents of Bionic's __res_params struct, i.e. sample_validity is stored at
     *   position RESOLVER_PARAMS_SAMPLE_VALIDITY, etc.
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void setResolverConfiguration(int netId, in @utf8InCpp String[] servers,
            in @utf8InCpp String[] domains, in int[] params);

    // Array indices for resolver stats.
    const int RESOLVER_STATS_SUCCESSES = 0;
    const int RESOLVER_STATS_ERRORS = 1;
    const int RESOLVER_STATS_TIMEOUTS = 2;
    const int RESOLVER_STATS_INTERNAL_ERRORS = 3;
    const int RESOLVER_STATS_RTT_AVG = 4;
    const int RESOLVER_STATS_LAST_SAMPLE_TIME = 5;
    const int RESOLVER_STATS_USABLE = 6;
    const int RESOLVER_STATS_COUNT = 7;

    /**
     * Retrieves the name servers, search domains and resolver stats associated with the given
     * network ID.
     *
     * @param netId the network ID of the network for which information should be retrieved.
     * @param servers the DNS servers that are currently configured for the network.
     * @param domains the search domains currently configured.
     * @param params the resolver parameters configured, i.e. the contents of __res_params in order.
     * @param stats the stats for each server in the order specified by RESOLVER_STATS_XXX
     *         constants, serialized as an int array. The contents of this array are the number of
     *         <ul>
     *           <li> successes,
     *           <li> errors,
     *           <li> timeouts,
     *           <li> internal errors,
     *           <li> the RTT average,
     *           <li> the time of the last recorded sample,
     *           <li> and an integer indicating whether the server is usable (1) or broken (0).
     *         </ul>
     *         in this order. For example, the timeout counter for server N is stored at position
     *         RESOLVER_STATS_COUNT*N + RESOLVER_STATS_TIMEOUTS
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void getResolverInfo(int netId, out @utf8InCpp String[] servers,
            out @utf8InCpp String[] domains, out int[] params, out int[] stats);

    // Private DNS function error codes.
    const int PRIVATE_DNS_SUCCESS = 0;
    const int PRIVATE_DNS_BAD_ADDRESS = 1;
    const int PRIVATE_DNS_BAD_PORT = 2;
    const int PRIVATE_DNS_UNKNOWN_ALGORITHM = 3;
    const int PRIVATE_DNS_BAD_FINGERPRINT = 4;

    /**
     * Adds a server to the list of DNS resolvers that support DNS over TLS.  After this action
     * succeeds, any subsequent call to setResolverConfiguration will opportunistically use DNS
     * over TLS if the specified server is on this list and is reachable on that network.
     *
     * @param server the DNS server's IP address.  If a private DNS server is already configured
     *        with this IP address, it will be overwritten.
     * @param port the port on which the server is listening, typically 853.
     * @param fingerprintAlgorithm the hash algorithm used to compute the fingerprints.  This should
     *        be a name in MessageDigest's format.  Currently "SHA-256" is the only supported
     *        algorithm. Set this to the empty string to disable fingerprint validation.
     * @param fingerprints the server's public key fingerprints as Base64 strings.
     *        These can be generated using MessageDigest and android.util.Base64.encodeToString.
     *        Currently "SHA-256" is the only supported algorithm. Set this to empty to disable
     *        fingerprint validation.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *         cause of the the failure.
     */
    void addPrivateDnsServer(in @utf8InCpp String server, int port,
             in @utf8InCpp String fingerprintAlgorithm, in @utf8InCpp String[] fingerprints);

    /**
     * Remove a server from the list of DNS resolvers that support DNS over TLS.
     *
     * @param server the DNS server's IP address.
     * @throws ServiceSpecificException in case of failure, with an error code indicating the
     *         cause of the the failure.
     */
    void removePrivateDnsServer(in @utf8InCpp String server);

    /**
     * Instruct the tethering DNS server to reevaluated serving interfaces.
     * This is needed to for the DNS server to observe changes in the set
     * of potential listening IP addresses. (Listening on wildcard addresses
     * can turn the device into an open resolver; b/7530468)
     *
     * TODO: Return something richer than just a boolean.
     */
    boolean tetherApplyDnsInterfaces();

    /**
     * Add/Remove and IP address from an interface.
     *
     * @param ifName the interface name
     * @param addrString the IP address to add/remove as a string literal
     * @param prefixLength the prefix length associated with this IP address
     *
     * @throws ServiceSpecificException in case of failure, with an error code corresponding to the
     *         unix errno.
     */
    void interfaceAddAddress(in @utf8InCpp String ifName, in @utf8InCpp String addrString,
            int prefixLength);
    void interfaceDelAddress(in @utf8InCpp String ifName, in @utf8InCpp String addrString,
            int prefixLength);

    /**
     * Set and get /proc/sys/net interface configuration parameters.
     *
     * @param family One of IPV4/IPV6 integers, indicating the desired address family directory.
     * @param which One of CONF/NEIGH integers, indicating the desired parameter category directory.
     * @param ifname The interface name portion of the path; may also be "all" or "default".
     * @param parameter The parameter name portion of the path.
     * @param value The value string to be written into the assembled path.
     */

    const int IPV4  = 4;
    const int IPV6  = 6;
    const int CONF  = 1;
    const int NEIGH = 2;
    void setProcSysNet(int family, int which, in @utf8InCpp String ifname,
            in @utf8InCpp String parameter, in @utf8InCpp String value);
    // TODO: add corresponding getProcSysNet().

    /**
     * Get/Set metrics reporting level.
     *
     * Reporting level is one of:
     *     0 (NONE)
     *     1 (METRICS)
     *     2 (FULL)
     */
    int getMetricsReportingLevel();
    void setMetricsReportingLevel(int level);

   /**
    * Reserve an SPI from the kernel
    *
    * @param transformId a unique identifier for allocated resources
    * @param direction DIRECTION_IN or DIRECTION_OUT
    * @param localAddress InetAddress as string for the local endpoint
    * @param remoteAddress InetAddress as string for the remote endpoint
    * @param spi a requested 32-bit unique ID or 0 to request random allocation
    * @return the SPI that was allocated or 0 if failed
    */
    int ipSecAllocateSpi(
            int transformId,
            int direction,
            in @utf8InCpp String localAddress,
            in @utf8InCpp String remoteAddress,
            int spi);

   /**
    * Create an IpSec Security Association describing how ip(v6) traffic will be encrypted
    * or decrypted.
    *
    * @param transformId a unique identifier for allocated resources
    * @param mode either Transport or Tunnel mode
    * @param direction DIRECTION_IN or DIRECTION_OUT
    * @param localAddress InetAddress as string for the local endpoint
    * @param remoteAddress InetAddress as string for the remote endpoint
    * @param underlyingNetworkHandle the networkHandle of the network to which the SA is applied
    * @param spi a 32-bit unique ID allocated to the user
    * @param authAlgo a string identifying the authentication algorithm to be used
    * @param authKey a byte array containing the authentication key
    * @param authTruncBits the truncation length of the MAC produced by the authentication algorithm
    * @param cryptAlgo a string identifying the encryption algorithm to be used
    * @param cryptKey a byte arrray containing the encryption key
    * @param cryptTruncBits unused parameter
    * @param encapType encapsulation type used (if any) for the udp encap socket
    * @param encapLocalPort the port number on the host to be used in encap packets
    * @param encapRemotePort the port number of the remote to be used for encap packets
    */
    void ipSecAddSecurityAssociation(
            int transformId,
            int mode,
            int direction,
            in @utf8InCpp String localAddress,
            in @utf8InCpp String remoteAddress,
            long underlyingNetworkHandle,
            int spi,
            in @utf8InCpp String authAlgo, in byte[] authKey, in int authTruncBits,
            in @utf8InCpp String cryptAlgo, in byte[] cryptKey, in int cryptTruncBits,
            int encapType,
            int encapLocalPort,
            int encapRemotePort);

   /**
    * Delete a previously created security association identified by the provided parameters
    *
    * @param transformId a unique identifier for allocated resources
    * @param direction DIRECTION_IN or DIRECTION_OUT
    * @param localAddress InetAddress as string for the local endpoint
    * @param remoteAddress InetAddress as string for the remote endpoint
    * @param spi a requested 32-bit unique ID allocated to the user
    */
    void ipSecDeleteSecurityAssociation(
            int transformId,
            int direction,
            in @utf8InCpp String localAddress,
            in @utf8InCpp String remoteAddress,
            int spi);

   /**
    * Apply a previously created SA to a specified socket, starting IPsec on that socket
    *
    * @param socket a user-provided socket that will have IPsec applied
    * @param transformId a unique identifier for allocated resources
    * @param direction DIRECTION_IN or DIRECTION_OUT
    * @param localAddress InetAddress as string for the local endpoint
    * @param remoteAddress InetAddress as string for the remote endpoint
    * @param spi a 32-bit unique ID allocated to the user (socket owner)
    */
    void ipSecApplyTransportModeTransform(
            in FileDescriptor socket,
            int transformId,
            int direction,
            in @utf8InCpp String localAddress,
            in @utf8InCpp String remoteAddress,
            int spi);

   /**
    * Remove an IPsec SA from a given socket. This will allow unencrypted traffic to flow
    * on that socket if a transform had been previously applied.
    *
    * @param socket a user-provided socket from which to remove any IPsec configuration
    */
    void ipSecRemoveTransportModeTransform(
            in FileDescriptor socket);

   /**
    * Request notification of wakeup packets arriving on an interface. Notifications will be
    * delivered to INetdEventListener.onWakeupEvent().
    *
    * @param ifName the interface
    * @param prefix arbitrary string used to identify wakeup sources in onWakeupEvent
    */
    void wakeupAddInterface(in @utf8InCpp String ifName, in @utf8InCpp String prefix, int mark, int mask);

   /**
    * Stop notification of wakeup packets arriving on an interface.
    *
    * @param ifName the interface
    * @param prefix arbitrary string used to identify wakeup sources in onWakeupEvent
    */
    void wakeupDelInterface(in @utf8InCpp String ifName, in @utf8InCpp String prefix, int mark, int mask);

    const int IPV6_ADDR_GEN_MODE_EUI64 = 0;
    const int IPV6_ADDR_GEN_MODE_NONE = 1;
    const int IPV6_ADDR_GEN_MODE_STABLE_PRIVACY = 2;
    const int IPV6_ADDR_GEN_MODE_RANDOM = 3;

    const int IPV6_ADDR_GEN_MODE_DEFAULT = 0;
   /**
    * Set IPv6 address generation mode. IPv6 should be disabled before changing mode.
    *
    * @param mode SLAAC address generation mechanism to use
    */
    void setIPv6AddrGenMode(in @utf8InCpp String ifName, int mode);
}
