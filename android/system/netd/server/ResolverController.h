/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _RESOLVER_CONTROLLER_H_
#define _RESOLVER_CONTROLLER_H_

#include <vector>
#include <netinet/in.h>
#include <linux/in.h>

struct __res_params;

namespace android {
namespace net {

class DumpWriter;
struct ResolverStats;

class ResolverController {
public:
    ResolverController() {};

    virtual ~ResolverController() {};

    // TODO: delete this function
    int setDnsServers(unsigned netId, const char* searchDomains, const char** servers,
            int numservers, const __res_params* params);

    // Given a netId and the address of an insecure (i.e. normal) DNS server, this method checks
    // if there is a known secure DNS server with the same IP address that has been validated as
    // accessible on this netId.  If so, it returns true, providing the server's address
    // (including port) and pin fingerprints (possibly empty) in the output parameters.
    // TODO: Add support for optional stronger security, by returning true even if the secure
    // server is not accessible.
    bool shouldUseTls(unsigned netId, const sockaddr_storage& insecureServer,
            sockaddr_storage* secureServer, std::set<std::vector<uint8_t>>* fingerprints);

    int clearDnsServers(unsigned netid);

    int flushDnsCache(unsigned netid);

    int getDnsInfo(unsigned netId, std::vector<std::string>* servers,
            std::vector<std::string>* domains, __res_params* params,
            std::vector<android::net::ResolverStats>* stats);

    // Binder specific functions, which convert between the binder int/string arrays and the
    // actual data structures, and call setDnsServer() / getDnsInfo() for the actual processing.
    int setResolverConfiguration(int32_t netId, const std::vector<std::string>& servers,
            const std::vector<std::string>& domains, const std::vector<int32_t>& params);

    int getResolverInfo(int32_t netId, std::vector<std::string>* servers,
            std::vector<std::string>* domains, std::vector<int32_t>* params,
            std::vector<int32_t>* stats);
    void dump(DumpWriter& dw, unsigned netId);

    int addPrivateDnsServer(const std::string& server, int32_t port,
            const std::string& fingerprintAlgorithm,
            const std::set<std::vector<uint8_t>>& fingerprints);
    int removePrivateDnsServer(const std::string& server);
};

}  // namespace net
}  // namespace android

#endif /* _RESOLVER_CONTROLLER_H_ */
