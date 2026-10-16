package com.v2ray.ang.enums

enum class RoutingType(val fileName: String) {
    VPN360("custom_routing_vpn360"), // VPN 360 default: no geo files needed, Russian sites direct
    WHITE("custom_routing_white"),
    BLACK("custom_routing_black"),
    GLOBAL("custom_routing_global"),
    WHITE_IRAN("custom_routing_white_iran"),
    WHITE_RUSSIA("custom_routing_white_russia")
}
