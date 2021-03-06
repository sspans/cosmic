(function (cloudStack, $) {
    var selectedNetworkOfferingHavingSG = false;
    var selectedNetworkOfferingHavingEIP = false;
    var selectedNetworkOfferingHavingELB = false;
    var returnedPublicVlanIpRanges = []; //public VlanIpRanges returned by API
    var configurationUseLocalStorage = false;
    var skipGuestTrafficStep = false;
    var selectedNetworkOfferingObj = {};

    // Makes URL string for traffic label
    var trafficLabelParam = function (trafficTypeID, data, physicalNetworkID) {
        var zoneType = data.zone.networkType;
        var hypervisor = data.zone.hypervisor;
        physicalNetworkID = zoneType == 'Advanced' ? physicalNetworkID : 0;
        var physicalNetwork = data.physicalNetworks ? data.physicalNetworks[physicalNetworkID] : null;
        var trafficConfig = physicalNetwork ? physicalNetwork.trafficTypeConfiguration[trafficTypeID] : null;

        var trafficLabel;
        if (trafficConfig != null) {
            if ('label' in trafficConfig) {
                trafficLabel = trafficConfig.label;
            }
            else {
                trafficLabel = '';

                if ('vSwitchName' in trafficConfig) {
                    trafficLabel += trafficConfig.vSwitchName;
                }
                if ('vlanId' in trafficConfig) {
                    if (trafficLabel.length > 0)
                        trafficLabel += ',';
                    trafficLabel += trafficConfig.vlanId;
                }
                if ('vSwitchType' in trafficConfig) {
                    if (trafficLabel.length > 0)
                        trafficLabel += ',';
                    trafficLabel += trafficConfig.vSwitchType;
                }

                if (trafficLabel.length == 0) { //trafficLabel == ''
                    trafficLabel = null;
                } else if (trafficLabel.length >= 1) {
                    if (trafficLabel.charAt(trafficLabel.length - 1) == ',') { //if last character is comma
                        trafficLabel = trafficLabel.substring(0, trafficLabel.length - 1); //remove the last character (which is comma)
                    }
                }
            }
        }

        var hypervisorAttr, trafficLabelStr;

        switch (hypervisor) {
            case 'XenServer':
                hypervisorAttr = 'xennetworklabel';
                break;
            case 'KVM':
                hypervisorAttr = 'kvmnetworklabel';
                break;
        }

        trafficLabelStr = trafficLabel ? '&' + hypervisorAttr + '=' + trafficLabel : '';

        return trafficLabelStr;
    };

    cloudStack.zoneWizard = {
        // Return required traffic types, for configure physical network screen
        requiredTrafficTypes: function (args) {
            if (args.data.zone.networkType == 'Basic') {
                if (selectedNetworkOfferingHavingEIP || selectedNetworkOfferingHavingELB) {
                    return [
                        'management',
                        'guest',
                        'public'
                    ];
                } else {
                    return [
                        'management',
                        'guest'
                    ];
                }
            } else { // args.data.zone.networkType == 'Advanced'
                if (args.data.zone.sgEnabled != true) {
                    return [
                        'management',
                        'public',
                        'guest'
                    ];
                } else {
                    return [
                        'management',
                        'guest'
                    ];
                }
            }
        },

        disabledTrafficTypes: function (args) {
            if (args.data.zone.networkType == 'Basic') {
                if (selectedNetworkOfferingHavingEIP || selectedNetworkOfferingHavingELB)
                    return [];
                else
                    return ['public'];
            } else { // args.data.zone.networkType == 'Advanced'
                if (args.data.zone.sgEnabled != true) {
                    return [];
                } else {
                    return ['public'];
                }
            }
        },

        cloneTrafficTypes: function (args) {
            if (args.data.zone.networkType == 'Advanced') {
                return ['guest'];
            } else {
                return [];
            }
        },

        customUI: {
            publicTrafficIPRange: function (args) {
                var multiEditData = [];
                var totalIndex = 0;

                return $('<div>').multiEdit({
                    context: args.context,
                    noSelect: true,
                    fields: {
                        'gateway': {
                            edit: true,
                            label: 'label.gateway'
                        },
                        'netmask': {
                            edit: true,
                            label: 'label.netmask'
                        },
                        'vlanid': {
                            edit: true,
                            label: 'label.vlan',
                            isOptional: true
                        },
                        'startip': {
                            edit: true,
                            label: 'label.start.IP',
                            validation: {
                                ipv4: true
                            }
                        },
                        'endip': {
                            edit: true,
                            label: 'label.end.IP',
                            validation: {
                                ipv4: true
                            }
                        },
                        'add-rule': {
                            label: 'label.add',
                            addButton: true
                        }
                    },
                    add: {
                        label: 'label.add',
                        action: function (args) {
                            multiEditData.push($.extend(args.data, {
                                index: totalIndex
                            }));

                            totalIndex++;
                            args.response.success();
                        }
                    },
                    actions: {
                        destroy: {
                            label: 'label.remove.rule',
                            action: function (args) {
                                multiEditData = $.grep(multiEditData, function (item) {
                                    return item.index != args.context.multiRule[0].index;
                                });
                                args.response.success();
                            }
                        }
                    },
                    dataProvider: function (args) {
                        args.response.success({
                            data: multiEditData
                        });
                    }
                });
            },

            storageTrafficIPRange: function (args) {

                var multiEditData = [];
                var totalIndex = 0;

                return $('<div>').multiEdit({
                    context: args.context,
                    noSelect: true,
                    fields: {
                        'gateway': {
                            edit: true,
                            label: 'label.gateway'
                        },
                        'netmask': {
                            edit: true,
                            label: 'label.netmask'
                        },
                        'vlan': {
                            edit: true,
                            label: 'label.vlan',
                            isOptional: true
                        },
                        'startip': {
                            edit: true,
                            label: 'label.start.IP'
                        },
                        'endip': {
                            edit: true,
                            label: 'label.end.IP'
                        },
                        'add-rule': {
                            label: 'label.add',
                            addButton: true
                        }
                    },
                    add: {
                        label: 'label.add',
                        action: function (args) {
                            multiEditData.push($.extend(args.data, {
                                index: totalIndex
                            }));

                            totalIndex++;
                            args.response.success();
                        }
                    },
                    actions: {
                        destroy: {
                            label: 'label.remove.rule',
                            action: function (args) {
                                multiEditData = $.grep(multiEditData, function (item) {
                                    return item.index != args.context.multiRule[0].index;
                                });
                                args.response.success();
                            }
                        }
                    },
                    dataProvider: function (args) {
                        args.response.success({
                            data: multiEditData
                        });
                    }
                });
            }
        },

        preFilters: {
            addPublicNetwork: function (args) {
                var isShown;
                var $publicTrafficDesc = $('.zone-wizard:visible').find('#add_zone_public_traffic_desc');
                if (args.data['network-model'] == 'Basic') {
                    if (selectedNetworkOfferingHavingSG == true && selectedNetworkOfferingHavingEIP == true && selectedNetworkOfferingHavingELB == true) {
                        isShown = true;
                    } else {
                        isShown = false;
                    }

                    $publicTrafficDesc.find('#for_basic_zone').css('display', 'inline');
                    $publicTrafficDesc.find('#for_advanced_zone').hide();
                } else { //args.data['network-model'] == 'Advanced'
                    if (args.data["zone-advanced-sg-enabled"] != "on")
                        isShown = true;
                    else
                        isShown = false;

                    $publicTrafficDesc.find('#for_advanced_zone').css('display', 'inline');
                    $publicTrafficDesc.find('#for_basic_zone').hide();
                }
                return isShown;
            },

            setupPhysicalNetwork: function (args) {
                if (args.data['network-model'] == 'Basic' && !(selectedNetworkOfferingHavingELB && selectedNetworkOfferingHavingEIP)) {
                    $('.setup-physical-network .info-desc.conditional.basic').show();
                    $('.setup-physical-network .info-desc.conditional.advanced').hide();
                    $('.subnav li.public-network').hide();
                } else {
                    $('.setup-physical-network .info-desc.conditional.basic').hide();
                    $('.setup-physical-network .info-desc.conditional.advanced').show();
                    $('.subnav li.public-network').show();
                }
                return true; // Both basic & advanced zones show physical network UI
            },

            configureGuestTraffic: function (args) {
                if ((args.data['network-model'] == 'Basic') || (args.data['network-model'] == 'Advanced' && args.data["zone-advanced-sg-enabled"] == "on")) {
                    $('.setup-guest-traffic').addClass('basic');
                    $('.setup-guest-traffic').removeClass('advanced');
                    skipGuestTrafficStep = false; //set value
                } else { //args.data['network-model'] == 'Advanced' && args.data["zone-advanced-sg-enabled"] !=    "on"
                    $('.setup-guest-traffic').removeClass('basic');
                    $('.setup-guest-traffic').addClass('advanced');

                    skipGuestTrafficStep = false; //set value

                }
                return !skipGuestTrafficStep;
            },

            configureStorageTraffic: function (args) {
                return $.grep(args.groupedData.physicalNetworks, function (network) {
                    return $.inArray('storage', network.trafficTypes) > -1;
                }).length;
            },

            addHost: function (args) {
                return true;
            },

            addPrimaryStorage: function (args) {
                if (args.data.localstorageenabled == 'on' && args.data.localstorageenabledforsystemvm == 'on') {
                    return false; //skip step only when both localstorage and localstorage for system vm are checked
                }
                return true;
            }
        },

        forms: {
            zone: {
                preFilter: function (args) {
                    var $form = args.$form;

                    if (args.data['network-model'] == 'Basic') { //Basic zone
                        args.$form.find('[rel=networkOfferingId]').show(); //will be used to create a guest network during zone creation
                        args.$form.find('[rel=guestcidraddress]').hide();

                        args.$form.find('[rel=ip6dns1]').hide();
                        args.$form.find('[rel=ip6dns2]').hide();
                    } else { //Advanced zone
                        if (args.data["zone-advanced-sg-enabled"] != "on") { //Advanced SG-disabled zone
                            args.$form.find('[rel=networkOfferingId]').hide();
                            args.$form.find('[rel=guestcidraddress]').show();

                            args.$form.find('[rel=ip6dns1]').show();
                            args.$form.find('[rel=ip6dns2]').show();
                        } else { //Advanced SG-enabled zone
                            args.$form.find('[rel=networkOfferingId]').show(); //will be used to create a guest network during zone creation
                            args.$form.find('[rel=guestcidraddress]').hide();

                            args.$form.find('[rel=ip6dns1]').hide();
                            args.$form.find('[rel=ip6dns2]').hide();
                        }
                    }
                },
                fields: {
                    name: {
                        label: 'label.name',
                        validation: {
                            required: true
                        },
                        desc: 'message.tooltip.zone.name'
                    },
                    ip4dns1: {
                        label: 'label.ipv4.dns1',
                        validation: {
                            required: true,
                            ipv4: true
                        },
                        desc: 'message.tooltip.dns.1'
                    },
                    ip4dns2: {
                        label: 'label.ipv4.dns2',
                        desc: 'message.tooltip.dns.2',
                        validation: {
                            ipv4: true
                        }
                    },

                    ip6dns1: {
                        label: 'label.ipv6.dns1',
                        desc: 'message.tooltip.dns.1',
                        validation: {
                            ipv6: true
                        }
                    },
                    ip6dns2: {
                        label: 'label.ipv6.dns2',
                        desc: 'message.tooltip.dns.2',
                        validation: {
                            ipv6: true
                        }
                    },

                    internaldns1: {
                        label: 'label.internal.dns.1',
                        validation: {
                            required: true,
                            ipv4: true
                        },
                        desc: 'message.tooltip.internal.dns.1'
                    },
                    internaldns2: {
                        label: 'label.internal.dns.2',
                        desc: 'message.tooltip.internal.dns.2',
                        validation: {
                            ipv4: true
                        }
                    },
                    hypervisor: {
                        label: 'label.hypervisor',
                        validation: {
                            required: true
                        },
                        select: function (args) {
                            $.ajax({
                                url: createURL('listHypervisors'),
                                async: false,
                                data: {
                                    listAll: true
                                },
                                success: function (json) {
                                    var items = json.listhypervisorsresponse.hypervisor;
                                    var array1 = [];

                                    var firstOption = "XenServer";
                                    var nonSupportedHypervisors = {};
                                    if (args.context.zones[0]['network-model'] == "Advanced" && args.context.zones[0]['zone-advanced-sg-enabled'] == "on") {
                                        firstOption = "KVM";
                                    }

                                    if (items != null) {
                                        for (var i = 0; i < items.length; i++) {
                                            if (items[i].name in nonSupportedHypervisors)
                                                continue;

                                            if (items[i].name == firstOption)
                                                array1.unshift({
                                                    id: items[i].name,
                                                    description: items[i].name
                                                });
                                            else
                                                array1.push({
                                                    id: items[i].name,
                                                    description: items[i].name
                                                });
                                        }
                                    }
                                    args.response.success({
                                        data: array1
                                    });
                                }
                            });
                        }
                    },
                    networkOfferingId: {
                        label: 'label.network.offering',
                        dependsOn: 'hypervisor',
                        select: function (args) {
                            var selectedNetworkOfferingObj = {};
                            var networkOfferingObjs = [];

                            args.$select.unbind("change").bind("change", function () {
                                //reset when different network offering is selected
                                selectedNetworkOfferingHavingSG = false;
                                selectedNetworkOfferingHavingEIP = false;
                                selectedNetworkOfferingHavingELB = false;

                                var selectedNetworkOfferingId = $(this).val();
                                $(networkOfferingObjs).each(function () {
                                    if (this.id == selectedNetworkOfferingId) {
                                        selectedNetworkOfferingObj = this;
                                        return false; //break $.each() loop
                                    }
                                });

                                if (selectedNetworkOfferingObj.havingSG == true)
                                    selectedNetworkOfferingHavingSG = true;
                                if (selectedNetworkOfferingObj.havingEIP == true)
                                    selectedNetworkOfferingHavingEIP = true;
                                if (selectedNetworkOfferingObj.havingELB == true)
                                    selectedNetworkOfferingHavingELB = true;
                            });


                            $.ajax({
                                url: createURL("listNetworkOfferings&state=Enabled&guestiptype=Shared"),
                                dataType: "json",
                                async: false,
                                success: function (json) {
                                    networkOfferingObjs = json.listnetworkofferingsresponse.networkoffering;
                                    var availableNetworkOfferingObjs = [];
                                    $(networkOfferingObjs).each(function () {
                                        var thisNetworkOffering = this;
                                        $(this.service).each(function () {
                                            var thisService = this;

                                            if (thisService.name == "SecurityGroup") {
                                                thisNetworkOffering.havingSG = true;
                                            } else if (thisService.name == "StaticNat") {
                                                $(thisService.capability).each(function () {
                                                    if (this.name == "ElasticIp" && this.value == "true") {
                                                        thisNetworkOffering.havingEIP = true;
                                                        return false; //break $.each() loop
                                                    }
                                                });
                                            } else if (thisService.name == "Lb") {
                                                $(thisService.capability).each(function () {
                                                    if (this.name == "ElasticLb" && this.value == "true") {
                                                        thisNetworkOffering.havingELB = true;
                                                        return false; //break $.each() loop
                                                    }
                                                });
                                            }
                                        });

                                        if (thisNetworkOffering.havingEIP == true && thisNetworkOffering.havingELB == true) { //EIP ELB
                                            if (args.context.zones[0]["network-model"] == "Advanced" && args.context.zones[0]["zone-advanced-sg-enabled"] == "on") { // Advanced SG-enabled zone doesn't support EIP ELB
                                                return true; //move to next item in $.each() loop
                                            }
                                        }

                                        if (args.context.zones[0]["network-model"] == "Advanced" && args.context.zones[0]["zone-advanced-sg-enabled"] == "on") { // Advanced SG-enabled zone
                                            if (thisNetworkOffering.havingSG != true) {
                                                return true; //move to next item in $.each() loop
                                            }
                                        }

                                        availableNetworkOfferingObjs.push(thisNetworkOffering);
                                    });

                                    args.response.success({
                                        data: $.map(availableNetworkOfferingObjs, function (offering) {
                                            return {
                                                id: offering.id,
                                                description: offering.name
                                            };
                                        })
                                    });

                                }
                            });
                        }
                    },
                    networkdomain: {
                        label: 'label.network.domain',
                        desc: 'message.tooltip.network.domain'
                    },
                    guestcidraddress: {
                        label: 'label.guest.cidr',
                        defaultValue: '10.1.1.0/24',
                        validation: {
                            required: false
                        }
                    },
                    isdedicated: {
                        isBoolean: true,
                        label: 'label.dedicated',
                        isChecked: false
                    },
                    domain: {
                        label: 'label.domain',
                        dependsOn: 'isdedicated',
                        isHidden: true,
                        select: function (args) {
                            $.ajax({
                                url: createURL("listDomains&listAll=true"),
                                data: {
                                    viewAll: true
                                },
                                dataType: "json",
                                async: false,
                                success: function (json) {
                                    domainObjs = json.listdomainsresponse.domain;
                                    args.response.success({
                                        data: $.map(domainObjs, function (domain) {
                                            return {
                                                id: domain.id,
                                                description: domain.path
                                            };
                                        })
                                    });
                                }
                            });
                        }
                    },

                    account: {
                        label: 'label.account',
                        isHidden: true,
                        dependsOn: 'isdedicated',
                        //docID:'helpAccountForDedication',
                        validation: {
                            required: false
                        }

                    },

                    localstorageenabled: {
                        label: 'label.local.storage.enabled',
                        isBoolean: true,
                        onChange: function (args) {

                        }
                    },

                    localstorageenabledforsystemvm: {
                        label: 'label.local.storage.enabled.system.vms',
                        isBoolean: true,
                        onChange: function (args) {

                        }
                    }
                }
            },

            pod: {
                fields: {
                    name: {
                        label: 'label.pod.name',
                        validation: {
                            required: true
                        },
                        desc: 'message.tooltip.pod.name'
                    },
                    reservedSystemGateway: {
                        label: 'label.reserved.system.gateway',
                        validation: {
                            required: true
                        },
                        desc: 'message.tooltip.reserved.system.gateway'
                    },
                    reservedSystemNetmask: {
                        label: 'label.reserved.system.netmask',
                        validation: {
                            required: true
                        },
                        desc: 'message.tooltip.reserved.system.netmask'
                    },
                    reservedSystemStartIp: {
                        label: 'label.start.reserved.system.IP',
                        validation: {
                            required: true,
                            ipv4: true
                        }
                    },
                    reservedSystemEndIp: {
                        label: 'label.end.reserved.system.IP',
                        validation: {
                            required: false,
                            ipv4: true
                        }
                    }
                }
            },

            basicPhysicalNetwork: {
                preFilter: function (args) {
                    if (args.data['network-model'] == 'Basic' && (selectedNetworkOfferingHavingELB || selectedNetworkOfferingHavingEIP)) {
                        args.$form.find('[rel=dedicated]').hide();
                    } else {
                        args.$form.find('[rel=dedicated]').show();
                    }
                    ;
                    cloudStack.preFilter.addLoadBalancerDevice
                },
                fields: {
                    ip: {
                        label: 'label.ip.address'
                    },
                    username: {
                        label: 'label.username'
                    },
                    password: {
                        label: 'label.password',
                        isPassword: true
                    },
                    publicinterface: {
                        label: 'label.public.interface'
                    },
                    privateinterface: {
                        label: 'label.private.interface'
                    },
                    numretries: {
                        label: 'label.numretries',
                        defaultValue: '2'
                    },
                    dedicated: {
                        label: 'label.dedicated',
                        isBoolean: true,
                        isChecked: false
                    },
                    capacity: {
                        label: 'label.capacity',
                        validation: {
                            required: false,
                            number: true
                        }
                    }
                }
            },

            guestTraffic: {
                preFilter: function (args) {
                    var $guestTrafficDesc = $('.zone-wizard:visible').find('#add_zone_guest_traffic_desc');
                    if ((args.data['network-model'] == 'Basic') || (args.data['network-model'] == 'Advanced' && args.data["zone-advanced-sg-enabled"] == "on")) {
                        $guestTrafficDesc.find('#for_basic_zone').css('display', 'inline');
                        $guestTrafficDesc.find('#for_advanced_zone').hide();
                    } else { //args.data['network-model'] == 'Advanced' && args.data["zone-advanced-sg-enabled"] !=    "on"
                        $guestTrafficDesc.find('#for_advanced_zone').css('display', 'inline');
                        $guestTrafficDesc.find('#for_basic_zone').hide();
                    }

                    var selectedZoneObj = {
                        networktype: args.data['network-model']
                    };

                    if (selectedZoneObj.networktype == 'Basic') {
                        args.$form.find('[rel="guestGateway"]').show();
                        args.$form.find('[rel="guestNetmask"]').show();
                        args.$form.find('[rel="guestStartIp"]').show();
                        args.$form.find('[rel="guestEndIp"]').show();
                        args.$form.find('[rel="vlanId"]').hide();
                        args.$form.find('[rel="vlanRange"]').hide();
                    } else if (selectedZoneObj.networktype == 'Advanced' && args.data["zone-advanced-sg-enabled"] == "on") {
                        args.$form.find('[rel="guestGateway"]').show();
                        args.$form.find('[rel="guestNetmask"]').show();
                        args.$form.find('[rel="guestStartIp"]').show();
                        args.$form.find('[rel="guestEndIp"]').show();
                        args.$form.find('[rel="vlanId"]').show();
                        args.$form.find('[rel="vlanRange"]').hide();
                    } else if (selectedZoneObj.networktype == 'Advanced' && args.data["zone-advanced-sg-enabled"] != "on") { //this conditional statement is useless because the div will be replaced with other div(multiple tabs in Advanced zone without SG) later
                        args.$form.find('[rel="guestGateway"]').hide();
                        args.$form.find('[rel="guestNetmask"]').hide();
                        args.$form.find('[rel="guestStartIp"]').hide();
                        args.$form.find('[rel="guestEndIp"]').hide();
                        args.$form.find('[rel="vlanId"]').hide();
                        args.$form.find('[rel="vlanRange"]').show();
                    }
                },

                fields: {
                    guestGateway: {
                        label: 'label.guest.gateway'
                    }, //Basic, Advanced with SG
                    guestNetmask: {
                        label: 'label.guest.netmask'
                    }, //Basic, Advanced with SG
                    guestStartIp: {
                        label: 'label.guest.start.ip'
                    }, //Basic, Advanced with SG
                    guestEndIp: {
                        label: 'label.guest.end.ip'
                    }, //Basic, Advanced with SG
                    vlanId: {
                        label: 'label.vlan.id'
                    }, //Advanced with SG

                    vlanRange: { //in multiple tabs (tabs is as many as Guest Traffic types in multiple physical networks in Advanced Zone without SG)
                        label: 'label.vlan.range',
                        range: ['vlanRangeStart', 'vlanRangeEnd'],
                        validation: {
                            required: false,
                            digits: true
                        } //Bug 13517 - AddZone wizard->Configure guest traffic: Vlan Range is optional
                    }
                }
            },
            cluster: {
                fields: {
                    hypervisor: {
                        label: 'label.hypervisor',
                        select: function (args) {
                            // Disable select -- selection is made on zone setup step
                            args.$select.attr('disabled', 'disabled');

                            $.ajax({
                                url: createURL("listHypervisors"),
                                dataType: "json",
                                async: false,
                                success: function (json) {
                                    var hypervisors = json.listhypervisorsresponse.hypervisor;
                                    var items = [];
                                    $(hypervisors).each(function () {
                                        items.push({
                                            id: this.name,
                                            description: this.name
                                        })
                                    });
                                    args.response.success({
                                        data: items
                                    });
                                    args.$select.val(args.context.zones[0].hypervisor);
                                    args.$select.change();
                                }
                            });
                        }
                    },
                    name: {
                        label: 'label.cluster.name',
                        validation: {
                            required: true
                        }
                    }
                }
            },
            host: {
                preFilter: function (args) {
                },

                fields: {
                    hostname: {
                        label: 'label.host.name',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    username: {
                        label: 'label.username',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    password: {
                        label: 'label.password',
                        validation: {
                            required: true
                        },
                        isHidden: true,
                        isPassword: true
                    },
                    //input_group="general" ends here

                    //always appear (begin)
                    hosttags: {
                        label: 'label.host.tags',
                        validation: {
                            required: false
                        }
                    }
                    //always appear (end)
                }
            },
            primaryStorage: {
                preFilter: function (args) {
                },

                fields: {
                    name: {
                        label: 'label.name',
                        validation: {
                            required: true
                        }
                    },

                    scope: {
                        label: 'label.scope',
                        select: function (args) {

                            var selectedHypervisorObj = {
                                hypervisortype: $.isArray(args.context.zones[0].hypervisor) ?
                                    // We want the cluster's hypervisor type
                                    args.context.zones[0].hypervisor[1] : args.context.zones[0].hypervisor
                            };

                            if (selectedHypervisorObj == null) {
                                return;
                            }

                            //zone-wide-primary-storage is supported only for KVM
                            if (selectedHypervisorObj.hypervisortype == "KVM") {
                                var scope = [];
                                scope.push({
                                    id: 'zone',
                                    description: _l('label.zone.wide')
                                });
                                scope.push({
                                    id: 'cluster',
                                    description: _l('label.cluster')
                                });
                                args.response.success({
                                    data: scope
                                });
                            } else {
                                var scope = [];
                                scope.push({
                                    id: 'cluster',
                                    description: _l('label.cluster')
                                });
                                args.response.success({
                                    data: scope
                                });
                            }
                        }
                    },

                    protocol: {
                        label: 'label.protocol',
                        validation: {
                            required: true
                        },
                        select: function (args) {
                            var selectedClusterObj = {
                                hypervisortype: $.isArray(args.context.zones[0].hypervisor) ?
                                    // We want the cluster's hypervisor type
                                    args.context.zones[0].hypervisor[1] : args.context.zones[0].hypervisor
                            };

                            if (selectedClusterObj == null) {
                                return;
                            }

                            if (selectedClusterObj.hypervisortype == "KVM") {
                                var items = [];
                                items.push({
                                    id: "nfs",
                                    description: "nfs"
                                });
                                items.push({
                                    id: "SharedMountPoint",
                                    description: "SharedMountPoint"
                                });
                                items.push({
                                    id: "rbd",
                                    description: "RBD"
                                });
                                items.push({
                                    id: "clvm",
                                    description: "CLVM"
                                });
                                items.push({
                                    id: "gluster",
                                    description: "Gluster"
                                });
                                args.response.success({
                                    data: items
                                });
                            } else if (selectedClusterObj.hypervisortype == "XenServer") {
                                var items = [];
                                items.push({
                                    id: "nfs",
                                    description: "nfs"
                                });
                                items.push({
                                    id: "PreSetup",
                                    description: "PreSetup"
                                });
                                items.push({
                                    id: "iscsi",
                                    description: "iscsi"
                                });
                                args.response.success({
                                    data: items
                                });
                            } else {
                                args.response.success({
                                    data: []
                                });
                            }

                            args.$select.change(function () {
                                var $form = $(this).closest('form');

                                var protocol = $(this).val();

                                $form.find('[rel=path]').find(".name").find("label").html('<span class=\"field-required\">*</span>' + _l('label.path') + ':');

                                if (protocol == null)
                                    return;

                                if (protocol == "nfs") {
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').css('display', 'block');

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "SMB") { //"SMB" show almost the same fields as "nfs" does, except 3 more SMB-specific fields.
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').css('display', 'block');

                                    $form.find('[rel=smbUsername]').css('display', 'block');
                                    $form.find('[rel=smbPassword]').css('display', 'block');
                                    $form.find('[rel=smbDomain]').css('display', 'block');

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "ocfs2") { //ocfs2 is the same as nfs, except no server field.
                                    $form.find('[rel=server]').hide();
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').css('display', 'block');

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "PreSetup") {
                                    $form.find('[rel=server]').hide();
                                    $form.find('[rel=server]').find(".value").find("input").val("localhost");

                                    $form.find('[rel=path]').css('display', 'block');
                                    $form.find('[rel=path]').find(".name").find("label").html('<span class=\"field-required\">*</span>' + _l('label.SR.name') + ':');

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "iscsi") {
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').hide();

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').css('display', 'block');
                                    $form.find('[rel=lun]').css('display', 'block');

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if ($(this).val() == "clvm") {
                                    $form.find('[rel=server]').hide();
                                    $form.find('[rel=server]').find(".value").find("input").val("localhost");

                                    $form.find('[rel=path]').hide();

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').css('display', 'inline-block');

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "vmfs") {
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').hide();

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').css('display', 'block');
                                    $form.find('[rel=vCenterDataStore]').css('display', 'block');

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "SharedMountPoint") { //"SharedMountPoint" show the same fields as "nfs" does.
                                    $form.find('[rel=server]').hide();
                                    $form.find('[rel=server]').find(".value").find("input").val("localhost");

                                    $form.find('[rel=path]').css('display', 'block');

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else if (protocol == "gluster") {
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=path]').hide();

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').css('display', 'block');
                                } else if (protocol == "rbd") {
                                    $form.find('[rel=rbdmonitor]').css('display', 'inline-block');
                                    $form.find('[rel=rbdmonitor]').find(".name").find("label").text("RADOS Monitor:");

                                    $form.find('[rel=rbdpool]').css('display', 'inline-block');
                                    $form.find('[rel=rbdpool]').find(".name").find("label").text("RADOS Pool:");

                                    $form.find('[rel=rbdid]').css('display', 'inline-block');
                                    $form.find('[rel=rbdid]').find(".name").find("label").text("RADOS User:");

                                    $form.find('[rel=rbdsecret]').css('display', 'inline-block');
                                    $form.find('[rel=rbdsecret]').find(".name").find("label").text("RADOS Secret:");

                                    $form.find('[rel=server]').hide();
                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();
                                    $form.find('[rel=volumegroup]').hide();
                                    $form.find('[rel=path]').hide();
                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                } else {
                                    $form.find('[rel=server]').css('display', 'block');
                                    $form.find('[rel=server]').find(".value").find("input").val("");

                                    $form.find('[rel=smbUsername]').hide();
                                    $form.find('[rel=smbPassword]').hide();
                                    $form.find('[rel=smbDomain]').hide();

                                    $form.find('[rel=iqn]').hide();
                                    $form.find('[rel=lun]').hide();

                                    $form.find('[rel=volumegroup]').hide();

                                    $form.find('[rel=vCenterDataCenter]').hide();
                                    $form.find('[rel=vCenterDataStore]').hide();

                                    $form.find('[rel=rbdmonitor]').hide();
                                    $form.find('[rel=rbdpool]').hide();
                                    $form.find('[rel=rbdid]').hide();
                                    $form.find('[rel=rbdsecret]').hide();

                                    $form.find('[rel=glustervolume]').hide();
                                }
                            });

                            args.$select.trigger("change");
                        }
                    },
                    server: {
                        label: 'label.server',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    //nfs
                    path: {
                        label: 'label.path',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    //SMB
                    smbDomain: {
                        label: 'label.smb.domain',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    smbUsername: {
                        label: 'label.smb.username',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    smbPassword: {
                        label: 'label.smb.password',
                        isPassword: true,
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },


                    //iscsi
                    iqn: {
                        label: 'label.target.iqn',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    lun: {
                        label: 'label.LUN.number',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    //clvm
                    volumegroup: {
                        label: 'label.volgroup',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    //vmfs
                    vCenterDataCenter: {
                        label: 'label.vcenter.datacenter',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    vCenterDataStore: {
                        label: 'label.vcenter.datastore',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    //gluster
                    glustervolume: {
                        label: 'label.gluster.volume',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },

                    // RBD
                    rbdmonitor: {
                        label: 'label.rbd.monitor',
                        docID: 'helpPrimaryStorageRBDMonitor',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    rbdpool: {
                        label: 'label.rbd.pool',
                        docID: 'helpPrimaryStorageRBDPool',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    rbdid: {
                        label: 'label.rbd.id',
                        docID: 'helpPrimaryStorageRBDId',
                        validation: {
                            required: false
                        },
                        isHidden: true
                    },
                    rbdsecret: {
                        label: 'label.rbd.secret',
                        docID: 'helpPrimaryStorageRBDSecret',
                        validation: {
                            required: false
                        },
                        isHidden: true
                    },

                    //always appear (begin)
                    storageTags: {
                        label: 'label.storage.tags',
                        validation: {
                            required: false
                        }
                    }
                    //always appear (end)
                }
            },
            secondaryStorage: {
                fields: {
                    provider: {
                        label: 'label.provider',
                        select: function (args) {
                            var storageproviders = [];
                            storageproviders.push({id: '', description: ''});

                            $.ajax({
                                url: createURL('listImageStores'),
                                async: true,
                                success: function (json) {
                                    storageproviders.push({id: 'NFS', description: 'NFS'});
                                    storageproviders.push({id: 'SMB', description: 'SMB/CIFS'});
                                    args.response.success({
                                        data: storageproviders
                                    });

                                    args.$select.change(function () {
                                        var $form = $(this).closest('form');
                                        var $fields = $form.find('.field');

                                        if ($(this).val() == "") {
                                            $fields.filter('[rel=name]').hide();

                                            //NFS
                                            $fields.filter('[rel=zoneid]').hide();
                                            $fields.filter('[rel=nfsServer]').hide();
                                            $fields.filter('[rel=path]').hide();

                                            //SMB
                                            $fields.filter('[rel=smbUsername]').hide();
                                            $fields.filter('[rel=smbPassword]').hide();
                                            $fields.filter('[rel=smbDomain]').hide();
                                        } else if ($(this).val() == "NFS") {
                                            $fields.filter('[rel=name]').css('display', 'inline-block');

                                            //NFS
                                            $fields.filter('[rel=zoneid]').css('display', 'inline-block');
                                            $fields.filter('[rel=nfsServer]').css('display', 'inline-block');
                                            $fields.filter('[rel=path]').css('display', 'inline-block');

                                            //SMB
                                            $fields.filter('[rel=smbUsername]').hide();
                                            $fields.filter('[rel=smbPassword]').hide();
                                            $fields.filter('[rel=smbDomain]').hide();
                                        } else if ($(this).val() == "SMB") {
                                            $fields.filter('[rel=name]').css('display', 'inline-block');

                                            //NFS
                                            $fields.filter('[rel=zoneid]').css('display', 'inline-block');
                                            $fields.filter('[rel=nfsServer]').css('display', 'inline-block');
                                            $fields.filter('[rel=path]').css('display', 'inline-block');

                                            //SMB
                                            $fields.filter('[rel=smbUsername]').css('display', 'inline-block');
                                            $fields.filter('[rel=smbPassword]').css('display', 'inline-block');
                                            $fields.filter('[rel=smbDomain]').css('display', 'inline-block');
                                        }
                                    });
                                    args.$select.change();
                                }
                            });
                        }
                    },

                    name: {
                        label: 'label.name',
                        isHidden: true
                    },

                    //NFS, SMB (begin)
                    nfsServer: {
                        label: 'label.server', //change label from "NFS Server" to "Server" since this field is also shown when provider "SMB/CIFS" is elected.
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    path: {
                        label: 'label.path',
                        validation: {
                            required: true
                        },
                        isHidden: true
                    },
                    //NFS, SMB (end)

                    //SMB (begin)
                    smbDomain: {
                        label: 'label.smb.domain',
                        validation: {
                            required: true
                        }
                    },
                    smbUsername: {
                        label: 'label.smb.username',
                        validation: {
                            required: true
                        }
                    },
                    smbPassword: {
                        label: 'label.smb.password',
                        isPassword: true,
                        validation: {
                            required: true
                        }
                    },
                    //SMB (end)
                }
            }
        },

        action: function (args) {
            var $wizard = args.wizard;
            var formData = args.data;
            var advZoneConfiguredVirtualRouterCount = 0; //for multiple physical networks in advanced zone. Each physical network has 2 virtual routers: regular one and VPC one.

            var success = args.response.success;
            var error = args.response.error;
            var message = args.response.message;
            //var data = args.data;
            var startFn = args.startFn;
            var data = args.data;

            var stepFns = {
                addZone: function () {
                    message(dictionary['message.creating.zone']);

                    var array1 = [];
                    var networkType = args.data.zone.networkType; //"Basic", "Advanced"
                    array1.push("&networktype=" + todb(networkType));

                    if (networkType == "Basic") {
                        if (selectedNetworkOfferingHavingSG == true)
                            array1.push("&securitygroupenabled=true");
                        else
                            array1.push("&securitygroupenabled=false");
                    } else { // networkType == "Advanced"
                        if (args.data.zone.sgEnabled != true) {
                            array1.push("&securitygroupenabled=false");

                            if (args.data.zone.guestcidraddress != null && args.data.zone.guestcidraddress.length > 0)
                                array1.push("&guestcidraddress=" + todb(args.data.zone.guestcidraddress));
                        } else { // args.data.zone.sgEnabled    == true
                            array1.push("&securitygroupenabled=true");
                        }
                    }

                    array1.push("&name=" + todb(args.data.zone.name));

                    if (args.data.zone.localstorageenabled == 'on') {
                        array1.push("&localstorageenabled=true");
                    }

                    //IPv4
                    if (args.data.zone.ip4dns1 != null && args.data.zone.ip4dns1.length > 0)
                        array1.push("&dns1=" + todb(args.data.zone.ip4dns1));
                    if (args.data.zone.ip4dns2 != null && args.data.zone.ip4dns2.length > 0)
                        array1.push("&dns2=" + todb(args.data.zone.ip4dns2));

                    //IPv6
                    if (args.data.zone.ip6dns1 != null && args.data.zone.ip6dns1.length > 0)
                        array1.push("&ip6dns1=" + todb(args.data.zone.ip6dns1));
                    if (args.data.zone.ip6dns2 != null && args.data.zone.ip6dns2.length > 0)
                        array1.push("&ip6dns2=" + todb(args.data.zone.ip6dns2));


                    array1.push("&internaldns1=" + todb(args.data.zone.internaldns1));

                    var internaldns2 = args.data.zone.internaldns2;
                    if (internaldns2 != null && internaldns2.length > 0)
                        array1.push("&internaldns2=" + todb(internaldns2));

                    if (args.data.zone.networkdomain != null && args.data.zone.networkdomain.length > 0)
                        array1.push("&domain=" + todb(args.data.zone.networkdomain));

                    $.ajax({
                        url: createURL("createZone" + array1.join("")),
                        dataType: "json",
                        async: false,
                        success: function (json) {
                            if (args.data.pluginFrom == null) { //from zone wizard, not from quick instsaller(args.data.pluginFrom != null && args.data.pluginFrom.name == 'installWizard') who doesn't have public checkbox
                                if (args.data.zone.isdedicated == 'on') { //dedicated checkbox in zone wizard is checked
                                    message(dictionary['message.dedicate.zone']);
                                    var data = {
                                        zoneid: json.createzoneresponse.zone.id
                                    };
                                    if (args.data.zone.domain != null)
                                        $.extend(data, {
                                            domainid: args.data.zone.domain
                                        });
                                    if (args.data.zone.account != "")
                                        $.extend(data, {
                                            account: args.data.zone.account
                                        });
                                    $.ajax({
                                        url: createURL('dedicateZone'),
                                        data: data,
                                        success: function (json) {
                                        }
                                    });
                                }
                            }

                            stepFns.addPhysicalNetworks({
                                data: $.extend(args.data, {
                                    returnedZone: json.createzoneresponse.zone
                                })
                            });
                        },
                        error: function (XMLHttpResponse) {
                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                            error('addZone', errorMsg, {
                                fn: 'addZone',
                                args: args
                            });
                        }
                    });
                },

                addPhysicalNetworks: function (args) {
                    message(_l('message.creating.physical.networks'));

                    var returnedPhysicalNetworks = [];

                    if (args.data.zone.networkType == "Basic") { //Basic zone ***
                        var requestedTrafficTypeCount = 2; //request guest traffic type, management traffic type
                        if (selectedNetworkOfferingHavingSG == true && selectedNetworkOfferingHavingEIP == true && selectedNetworkOfferingHavingELB == true)
                            requestedTrafficTypeCount++; //request public traffic type

                        //Basic zone has only one physical network
                        var array1 = [];
                        if ("physicalNetworks" in args.data) { //from add-zone-wizard
                            array1.push("&name=" + todb(args.data.physicalNetworks[0].name));
                        } else { //from quick-install-wizard
                            array1.push("&name=PhysicalNetworkInBasicZone");
                        }

                        $.ajax({
                            url: createURL("createPhysicalNetwork&zoneid=" + args.data.returnedZone.id + array1.join("")),
                            dataType: "json",
                            success: function (json) {
                                var jobId = json.createphysicalnetworkresponse.jobid;
                                var createPhysicalNetworkIntervalID = setInterval(function () {
                                    $.ajax({
                                        url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                        dataType: "json",
                                        success: function (json) {
                                            var result = json.queryasyncjobresultresponse;
                                            if (result.jobstatus == 0) {
                                                return; //Job has not completed
                                            } else {
                                                clearInterval(createPhysicalNetworkIntervalID);

                                                if (result.jobstatus == 1) {
                                                    var returnedBasicPhysicalNetwork = result.jobresult.physicalnetwork;
                                                    var label = returnedBasicPhysicalNetwork.id + trafficLabelParam('guest', data);
                                                    var returnedTrafficTypes = [];

                                                    $.ajax({
                                                        url: createURL("addTrafficType&trafficType=Guest&physicalnetworkid=" + label),
                                                        dataType: "json",
                                                        success: function (json) {
                                                            var jobId = json.addtraffictyperesponse.jobid;
                                                            var addGuestTrafficTypeIntervalID = setInterval(function () {
                                                                $.ajax({
                                                                    url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                                    dataType: "json",
                                                                    success: function (json) {
                                                                        var result = json.queryasyncjobresultresponse;
                                                                        if (result.jobstatus == 0) {
                                                                            return; //Job has not completed
                                                                        } else {
                                                                            clearInterval(addGuestTrafficTypeIntervalID);

                                                                            if (result.jobstatus == 1) {
                                                                                returnedTrafficTypes.push(result.jobresult.traffictype);

                                                                                if (returnedTrafficTypes.length == requestedTrafficTypeCount) { //all requested traffic types have been added
                                                                                    returnedBasicPhysicalNetwork.returnedTrafficTypes = returnedTrafficTypes;

                                                                                    stepFns.configurePhysicalNetwork({
                                                                                        data: $.extend(args.data, {
                                                                                            returnedBasicPhysicalNetwork: returnedBasicPhysicalNetwork
                                                                                        })
                                                                                    });
                                                                                }
                                                                            } else if (result.jobstatus == 2) {
                                                                                alert("Failed to add Guest traffic type to basic zone. Error: " + _s(result.jobresult.errortext));
                                                                            }
                                                                        }
                                                                    },
                                                                    error: function (XMLHttpResponse) {
                                                                        var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                        alert("Failed to add Guest traffic type to basic zone. Error: " + errorMsg);
                                                                    }
                                                                });
                                                            }, g_queryAsyncJobResultInterval);
                                                        }
                                                    });

                                                    label = trafficLabelParam('management', data);

                                                    $.ajax({
                                                        url: createURL("addTrafficType&trafficType=Management&physicalnetworkid=" + returnedBasicPhysicalNetwork.id + label),
                                                        dataType: "json",
                                                        success: function (json) {
                                                            var jobId = json.addtraffictyperesponse.jobid;
                                                            var addManagementTrafficTypeIntervalID = setInterval(function () {
                                                                $.ajax({
                                                                    url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                                    dataType: "json",
                                                                    success: function (json) {
                                                                        var result = json.queryasyncjobresultresponse;
                                                                        if (result.jobstatus == 0) {
                                                                            return; //Job has not completed
                                                                        } else {
                                                                            clearInterval(addManagementTrafficTypeIntervalID);

                                                                            if (result.jobstatus == 1) {
                                                                                returnedTrafficTypes.push(result.jobresult.traffictype);

                                                                                if (returnedTrafficTypes.length == requestedTrafficTypeCount) { //all requested traffic types have been added
                                                                                    returnedBasicPhysicalNetwork.returnedTrafficTypes = returnedTrafficTypes;

                                                                                    stepFns.configurePhysicalNetwork({
                                                                                        data: $.extend(args.data, {
                                                                                            returnedBasicPhysicalNetwork: returnedBasicPhysicalNetwork
                                                                                        })
                                                                                    });
                                                                                }
                                                                            } else if (result.jobstatus == 2) {
                                                                                alert("Failed to add Management traffic type to basic zone. Error: " + _s(result.jobresult.errortext));
                                                                            }
                                                                        }
                                                                    },
                                                                    error: function (XMLHttpResponse) {
                                                                        var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                        alert("Failed to add Management traffic type to basic zone. Error: " + errorMsg);
                                                                    }
                                                                });
                                                            }, g_queryAsyncJobResultInterval);
                                                        }
                                                    });

                                                    // Storage traffic
                                                    if (data.physicalNetworks &&
                                                        $.inArray('storage', data.physicalNetworks[0].trafficTypes) > -1) {
                                                        label = trafficLabelParam('storage', data);
                                                        $.ajax({
                                                            url: createURL('addTrafficType&physicalnetworkid=' + returnedBasicPhysicalNetwork.id + '&trafficType=Storage' + label),
                                                            dataType: "json",
                                                            success: function (json) {
                                                                var jobId = json.addtraffictyperesponse.jobid;
                                                                var addStorageTrafficTypeIntervalID = setInterval(function () {
                                                                    $.ajax({
                                                                        url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                                        dataType: "json",
                                                                        success: function (json) {
                                                                            var result = json.queryasyncjobresultresponse;
                                                                            if (result.jobstatus == 0) {
                                                                                return; //Job has not completed
                                                                            } else {
                                                                                clearInterval(addStorageTrafficTypeIntervalID);

                                                                                if (result.jobstatus == 1) {
                                                                                    returnedTrafficTypes.push(result.jobresult.traffictype);

                                                                                    if (returnedTrafficTypes.length == requestedTrafficTypeCount) { //all requested traffic types have been added
                                                                                        returnedBasicPhysicalNetwork.returnedTrafficTypes = returnedTrafficTypes;

                                                                                        stepFns.configurePhysicalNetwork({
                                                                                            data: $.extend(args.data, {
                                                                                                returnedBasicPhysicalNetwork: returnedBasicPhysicalNetwork
                                                                                            })
                                                                                        });
                                                                                    }
                                                                                } else if (result.jobstatus == 2) {
                                                                                    alert("Failed to add Management traffic type to basic zone. Error: " + _s(result.jobresult.errortext));
                                                                                }
                                                                            }
                                                                        },
                                                                        error: function (XMLHttpResponse) {
                                                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                            alert("Failed to add Management traffic type to basic zone. Error: " + errorMsg);
                                                                        }
                                                                    });
                                                                }, g_queryAsyncJobResultInterval);
                                                            }
                                                        });
                                                    }

                                                    if (selectedNetworkOfferingHavingSG == true && selectedNetworkOfferingHavingEIP == true && selectedNetworkOfferingHavingELB == true) {
                                                        label = trafficLabelParam('public', data);
                                                        $.ajax({
                                                            url: createURL("addTrafficType&trafficType=Public&physicalnetworkid=" + returnedBasicPhysicalNetwork.id + label),
                                                            dataType: "json",
                                                            success: function (json) {
                                                                var jobId = json.addtraffictyperesponse.jobid;
                                                                var addPublicTrafficTypeIntervalID = setInterval(function () {
                                                                    $.ajax({
                                                                        url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                                        dataType: "json",
                                                                        success: function (json) {
                                                                            var result = json.queryasyncjobresultresponse;
                                                                            if (result.jobstatus == 0) {
                                                                                return; //Job has not completed
                                                                            } else {
                                                                                clearInterval(addPublicTrafficTypeIntervalID);

                                                                                if (result.jobstatus == 1) {
                                                                                    returnedTrafficTypes.push(result.jobresult.traffictype);

                                                                                    if (returnedTrafficTypes.length == requestedTrafficTypeCount) { //all requested traffic types have been added
                                                                                        returnedBasicPhysicalNetwork.returnedTrafficTypes = returnedTrafficTypes;

                                                                                        stepFns.configurePhysicalNetwork({
                                                                                            data: $.extend(args.data, {
                                                                                                returnedBasicPhysicalNetwork: returnedBasicPhysicalNetwork
                                                                                            })
                                                                                        });
                                                                                    }
                                                                                } else if (result.jobstatus == 2) {
                                                                                    alert("Failed to add Public traffic type to basic zone. Error: " + _s(result.jobresult.errortext));
                                                                                }
                                                                            }
                                                                        },
                                                                        error: function (XMLHttpResponse) {
                                                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                            alert("Failed to add Public traffic type to basic zone. Error: " + errorMsg);
                                                                        }
                                                                    });
                                                                }, g_queryAsyncJobResultInterval);
                                                            }
                                                        });
                                                    }
                                                } else if (result.jobstatus == 2) {
                                                    alert("createPhysicalNetwork failed. Error: " + _s(result.jobresult.errortext));
                                                }
                                            }
                                        },
                                        error: function (XMLHttpResponse) {
                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                            alert("createPhysicalNetwork failed. Error: " + errorMsg);
                                        }
                                    });
                                }, g_queryAsyncJobResultInterval);

                            }
                        });
                    } else if (args.data.zone.networkType == "Advanced") {
                        $(args.data.physicalNetworks).each(function (index) {
                            var thisPhysicalNetwork = this;
                            var array1 = [];
                            array1.push("&name=" + todb(thisPhysicalNetwork.name));
                            if (thisPhysicalNetwork.isolationMethod != null && thisPhysicalNetwork.isolationMethod.length > 0)
                                array1.push("&isolationmethods=" + todb(thisPhysicalNetwork.isolationMethod));
                            $.ajax({
                                url: createURL("createPhysicalNetwork&zoneid=" + args.data.returnedZone.id + array1.join("")),
                                dataType: "json",
                                success: function (json) {
                                    var jobId = json.createphysicalnetworkresponse.jobid;
                                    var createPhysicalNetworkIntervalID = setInterval(function () {
                                        $.ajax({
                                            url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                            dataType: "json",
                                            success: function (json) {
                                                var result = json.queryasyncjobresultresponse;
                                                if (result.jobstatus == 0) {
                                                    return; //Job has not completed
                                                } else {
                                                    clearInterval(createPhysicalNetworkIntervalID);

                                                    if (result.jobstatus == 1) {
                                                        var returnedPhysicalNetwork = result.jobresult.physicalnetwork;
                                                        returnedPhysicalNetwork.originalId = thisPhysicalNetwork.id;

                                                        var returnedTrafficTypes = [];
                                                        var label; // Traffic type label
                                                        $(thisPhysicalNetwork.trafficTypes).each(function () {
                                                            var thisTrafficType = this;
                                                            var apiCmd = "addTrafficType&physicalnetworkid=" + returnedPhysicalNetwork.id;
                                                            if (thisTrafficType == "public") {
                                                                apiCmd += "&trafficType=Public";
                                                                label = trafficLabelParam('public', data, index);
                                                            } else if (thisTrafficType == "management") {
                                                                apiCmd += "&trafficType=Management";
                                                                label = trafficLabelParam('management', data, index);
                                                            } else if (thisTrafficType == "guest") {
                                                                apiCmd += "&trafficType=Guest";
                                                                label = trafficLabelParam('guest', data, index);
                                                            } else if (thisTrafficType == "storage") {
                                                                apiCmd += "&trafficType=Storage";
                                                                label = trafficLabelParam('storage', data, index);
                                                            }

                                                            $.ajax({
                                                                url: createURL(apiCmd + label),
                                                                dataType: "json",
                                                                success: function (json) {
                                                                    var jobId = json.addtraffictyperesponse.jobid;
                                                                    var addTrafficTypeIntervalID = setInterval(function () {
                                                                        $.ajax({
                                                                            url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                                            dataType: "json",
                                                                            success: function (json) {
                                                                                var result = json.queryasyncjobresultresponse;
                                                                                if (result.jobstatus == 0) {
                                                                                    return; //Job has not completed
                                                                                } else {
                                                                                    clearInterval(addTrafficTypeIntervalID);

                                                                                    if (result.jobstatus == 1) {
                                                                                        returnedTrafficTypes.push(result.jobresult.traffictype);

                                                                                        if (returnedTrafficTypes.length == thisPhysicalNetwork.trafficTypes.length) { //this physical network is complete (specified traffic types are added)
                                                                                            returnedPhysicalNetwork.returnedTrafficTypes = returnedTrafficTypes;
                                                                                            returnedPhysicalNetworks.push(returnedPhysicalNetwork);

                                                                                            if (returnedPhysicalNetworks.length == args.data.physicalNetworks.length) { //all physical networks are complete
                                                                                                stepFns.configurePhysicalNetwork({
                                                                                                    data: $.extend(args.data, {
                                                                                                        returnedPhysicalNetworks: returnedPhysicalNetworks
                                                                                                    })
                                                                                                });
                                                                                            }
                                                                                        }
                                                                                    } else if (result.jobstatus == 2) {
                                                                                        alert(apiCmd + " failed. Error: " + _s(result.jobresult.errortext));
                                                                                    }
                                                                                }
                                                                            },
                                                                            error: function (XMLHttpResponse) {
                                                                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                alert(apiCmd + " failed. Error: " + errorMsg);
                                                                            }
                                                                        });
                                                                    }, g_queryAsyncJobResultInterval);
                                                                }
                                                            });
                                                        });
                                                    } else if (result.jobstatus == 2) {
                                                        alert("createPhysicalNetwork failed. Error: " + _s(result.jobresult.errortext));
                                                    }
                                                }
                                            },
                                            error: function (XMLHttpResponse) {
                                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                alert("createPhysicalNetwork failed. Error: " + errorMsg);
                                            }
                                        });
                                    }, g_queryAsyncJobResultInterval);
                                }
                            });
                        });
                    }
                },

                //afterCreateZonePhysicalNetworkTrafficTypes: enable physical network, enable virtual router element, enable network service provider
                configurePhysicalNetwork: function (args) {
                    message(_l('message.configuring.physical.networks'));

                    if (args.data.zone.networkType == "Basic") {
                        $.ajax({
                            url: createURL("updatePhysicalNetwork&state=Enabled&id=" + args.data.returnedBasicPhysicalNetwork.id),
                            dataType: "json",
                            success: function (json) {
                                var enablePhysicalNetworkIntervalID = setInterval(function () {
                                    $.ajax({
                                        url: createURL("queryAsyncJobResult&jobId=" + json.updatephysicalnetworkresponse.jobid),
                                        dataType: "json",
                                        success: function (json) {
                                            var result = json.queryasyncjobresultresponse;
                                            if (result.jobstatus == 0) {
                                                return; //Job has not completed
                                            } else {
                                                clearInterval(enablePhysicalNetworkIntervalID);

                                                if (result.jobstatus == 1) {
                                                    //alert("updatePhysicalNetwork succeeded.");

                                                    // get network service provider ID of Virtual Router
                                                    var virtualRouterProviderId;
                                                    $.ajax({
                                                        url: createURL("listNetworkServiceProviders&name=VirtualRouter&physicalNetworkId=" + args.data.returnedBasicPhysicalNetwork.id),
                                                        dataType: "json",
                                                        async: false,
                                                        success: function (json) {
                                                            var items = json.listnetworkserviceprovidersresponse.networkserviceprovider;
                                                            if (items != null && items.length > 0) {
                                                                virtualRouterProviderId = items[0].id;
                                                            }
                                                        }
                                                    });
                                                    if (virtualRouterProviderId == null) {
                                                        alert("error: listNetworkServiceProviders API doesn't return VirtualRouter provider ID");
                                                        return;
                                                    }

                                                    var virtualRouterElementId;
                                                    $.ajax({
                                                        url: createURL("listVirtualRouterElements&nspid=" + virtualRouterProviderId),
                                                        dataType: "json",
                                                        async: false,
                                                        success: function (json) {
                                                            var items = json.listvirtualrouterelementsresponse.virtualrouterelement;
                                                            if (items != null && items.length > 0) {
                                                                virtualRouterElementId = items[0].id;
                                                            }
                                                        }
                                                    });
                                                    if (virtualRouterElementId == null) {
                                                        alert("error: listVirtualRouterElements API doesn't return Virtual Router Element Id");
                                                        return;
                                                    }

                                                    $.ajax({
                                                        url: createURL("configureVirtualRouterElement&enabled=true&id=" + virtualRouterElementId),
                                                        dataType: "json",
                                                        async: false,
                                                        success: function (json) {
                                                            var enableVirtualRouterElementIntervalID = setInterval(function () {
                                                                $.ajax({
                                                                    url: createURL("queryAsyncJobResult&jobId=" + json.configurevirtualrouterelementresponse.jobid),
                                                                    dataType: "json",
                                                                    success: function (json) {
                                                                        var result = json.queryasyncjobresultresponse;
                                                                        if (result.jobstatus == 0) {
                                                                            return; //Job has not completed
                                                                        } else {
                                                                            clearInterval(enableVirtualRouterElementIntervalID);

                                                                            if (result.jobstatus == 1) {
                                                                                //alert("configureVirtualRouterElement succeeded.");

                                                                                if (args.data.pluginFrom != null && args.data.pluginFrom.name == "installWizard") {
                                                                                    selectedNetworkOfferingObj = args.data.pluginFrom.selectedNetworkOffering;
                                                                                }

                                                                                var data = {
                                                                                    id: virtualRouterProviderId,
                                                                                    state: 'Enabled'
                                                                                };

                                                                                $.ajax({
                                                                                    url: createURL("updateNetworkServiceProvider"),
                                                                                    data: data,
                                                                                    async: false,
                                                                                    success: function (json) {
                                                                                        var enableVirtualRouterProviderIntervalID = setInterval(function () {
                                                                                            $.ajax({
                                                                                                url: createURL("queryAsyncJobResult&jobId=" + json.updatenetworkserviceproviderresponse.jobid),
                                                                                                dataType: "json",
                                                                                                success: function (json) {
                                                                                                    var result = json.queryasyncjobresultresponse;
                                                                                                    if (result.jobstatus == 0) {
                                                                                                        return; //Job has not completed
                                                                                                    } else {
                                                                                                        clearInterval(enableVirtualRouterProviderIntervalID);

                                                                                                        if (result.jobstatus == 1) {
                                                                                                            //alert("Virtual Router Provider is enabled");

                                                                                                            if (args.data.pluginFrom != null && args.data.pluginFrom.name == "installWizard") {
                                                                                                                selectedNetworkOfferingHavingSG = args.data.pluginFrom.selectedNetworkOfferingHavingSG;
                                                                                                            }
                                                                                                            if (selectedNetworkOfferingHavingSG == true) { //need to Enable security group provider first
                                                                                                                message(_l('message.enabling.security.group.provider'));

                                                                                                                // get network service provider ID of Security Group
                                                                                                                var securityGroupProviderId;
                                                                                                                $.ajax({
                                                                                                                    url: createURL("listNetworkServiceProviders&name=SecurityGroupProvider&physicalNetworkId=" + args.data.returnedBasicPhysicalNetwork.id),
                                                                                                                    dataType: "json",
                                                                                                                    async: false,
                                                                                                                    success: function (json) {
                                                                                                                        var items = json.listnetworkserviceprovidersresponse.networkserviceprovider;
                                                                                                                        if (items != null && items.length > 0) {
                                                                                                                            securityGroupProviderId = items[0].id;
                                                                                                                        }
                                                                                                                    }
                                                                                                                });
                                                                                                                if (securityGroupProviderId == null) {
                                                                                                                    alert("error: listNetworkServiceProviders API doesn't return security group provider ID");
                                                                                                                    return;
                                                                                                                }

                                                                                                                $.ajax({
                                                                                                                    url: createURL("updateNetworkServiceProvider&state=Enabled&id=" + securityGroupProviderId),
                                                                                                                    dataType: "json",
                                                                                                                    async: false,
                                                                                                                    success: function (json) {
                                                                                                                        var enableSecurityGroupProviderIntervalID = setInterval(function () {
                                                                                                                            $.ajax({
                                                                                                                                url: createURL("queryAsyncJobResult&jobId=" + json.updatenetworkserviceproviderresponse.jobid),
                                                                                                                                dataType: "json",
                                                                                                                                success: function (json) {
                                                                                                                                    var result = json.queryasyncjobresultresponse;
                                                                                                                                    if (result.jobstatus == 0) {
                                                                                                                                        return; //Job has not completed
                                                                                                                                    } else {
                                                                                                                                        clearInterval(enableSecurityGroupProviderIntervalID);

                                                                                                                                        if (result.jobstatus == 2) {
                                                                                                                                            alert("failed to enable security group provider. Error: " + _s(result.jobresult.errortext));
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                },
                                                                                                                                error: function (XMLHttpResponse) {
                                                                                                                                    var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                                                                    alert("failed to enable security group provider. Error: " + errorMsg);
                                                                                                                                }
                                                                                                                            });
                                                                                                                        }, g_queryAsyncJobResultInterval);
                                                                                                                    }
                                                                                                                });
                                                                                                            }
                                                                                                        } else if (result.jobstatus == 2) {
                                                                                                            alert("failed to enable Virtual Router Provider. Error: " + _s(result.jobresult.errortext));
                                                                                                        }
                                                                                                    }
                                                                                                },
                                                                                                error: function (XMLHttpResponse) {
                                                                                                    var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                                    alert("failed to enable Virtual Router Provider. Error: " + errorMsg);
                                                                                                }
                                                                                            });
                                                                                        }, g_queryAsyncJobResultInterval);
                                                                                    }
                                                                                });
                                                                            } else if (result.jobstatus == 2) {
                                                                                alert("configureVirtualRouterElement failed. Error: " + _s(result.jobresult.errortext));
                                                                            }
                                                                        }
                                                                    },
                                                                    error: function (XMLHttpResponse) {
                                                                        var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                        alert("configureVirtualRouterElement failed. Error: " + errorMsg);
                                                                    }
                                                                });
                                                            }, g_queryAsyncJobResultInterval);
                                                        }
                                                    });
                                                } else if (result.jobstatus == 2) {
                                                    alert("updatePhysicalNetwork failed. Error: " + _s(result.jobresult.errortext));
                                                }
                                            }
                                        },
                                        error: function (XMLHttpResponse) {
                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                            alert("updatePhysicalNetwork failed. Error: " + errorMsg);
                                        }
                                    });
                                }, g_queryAsyncJobResultInterval);
                            }
                        });
                    } else if (args.data.zone.networkType == "Advanced") {
                        $(args.data.returnedPhysicalNetworks).each(function () {
                            var thisPhysicalNetwork = this;
                            $.ajax({
                                url: createURL("updatePhysicalNetwork&state=Enabled&id=" + thisPhysicalNetwork.id),
                                dataType: "json",
                                success: function (json) {
                                    var jobId = json.updatephysicalnetworkresponse.jobid;
                                    var enablePhysicalNetworkIntervalID = setInterval(function () {
                                        $.ajax({
                                            url: createURL("queryAsyncJobResult&jobId=" + jobId),
                                            dataType: "json",
                                            success: function (json) {
                                                var result = json.queryasyncjobresultresponse;
                                                if (result.jobstatus == 0) {
                                                    return; //Job has not completed
                                                } else {
                                                    clearInterval(enablePhysicalNetworkIntervalID);

                                                    if (result.jobstatus == 1) {
                                                        //alert("enable physical network succeeded.");

                                                        // ***** Virtual Router ***** (begin) *****
                                                        var virtualRouterProviderId;
                                                        $.ajax({
                                                            url: createURL("listNetworkServiceProviders&name=VirtualRouter&physicalNetworkId=" + thisPhysicalNetwork.id),
                                                            dataType: "json",
                                                            async: false,
                                                            success: function (json) {
                                                                var items = json.listnetworkserviceprovidersresponse.networkserviceprovider;
                                                                if (items != null && items.length > 0) {
                                                                    virtualRouterProviderId = items[0].id;
                                                                }
                                                            }
                                                        });
                                                        if (virtualRouterProviderId == null) {
                                                            alert("error: listNetworkServiceProviders API doesn't return VirtualRouter provider ID");
                                                            return;
                                                        }

                                                        var virtualRouterElementId;
                                                        $.ajax({
                                                            url: createURL("listVirtualRouterElements&nspid=" + virtualRouterProviderId),
                                                            dataType: "json",
                                                            async: false,
                                                            success: function (json) {
                                                                var items = json.listvirtualrouterelementsresponse.virtualrouterelement;
                                                                if (items != null && items.length > 0) {
                                                                    virtualRouterElementId = items[0].id;
                                                                }
                                                            }
                                                        });
                                                        if (virtualRouterElementId == null) {
                                                            alert("error: listVirtualRouterElements API doesn't return Virtual Router Element Id");
                                                            return;
                                                        }

                                                        $.ajax({
                                                            url: createURL("configureVirtualRouterElement&enabled=true&id=" + virtualRouterElementId),
                                                            dataType: "json",
                                                            async: false,
                                                            success: function (json) {
                                                                var jobId = json.configurevirtualrouterelementresponse.jobid;
                                                                var enableVirtualRouterElementIntervalID = setInterval(function () {
                                                                    $.ajax({
                                                                        url: createURL("queryAsyncJobResult&jobId=" + jobId),
                                                                        dataType: "json",
                                                                        success: function (json) {
                                                                            var result = json.queryasyncjobresultresponse;
                                                                            if (result.jobstatus == 0) {
                                                                                return; //Job has not completed
                                                                            } else {
                                                                                clearInterval(enableVirtualRouterElementIntervalID);

                                                                                if (result.jobstatus == 1) { //configureVirtualRouterElement succeeded
                                                                                    $.ajax({
                                                                                        url: createURL("updateNetworkServiceProvider&state=Enabled&id=" + virtualRouterProviderId),
                                                                                        dataType: "json",
                                                                                        async: false,
                                                                                        success: function (json) {
                                                                                            var jobId = json.updatenetworkserviceproviderresponse.jobid;
                                                                                            var enableVirtualRouterProviderIntervalID = setInterval(function () {
                                                                                                $.ajax({
                                                                                                    url: createURL("queryAsyncJobResult&jobId=" + jobId),
                                                                                                    dataType: "json",
                                                                                                    success: function (json) {
                                                                                                        var result = json.queryasyncjobresultresponse;
                                                                                                        if (result.jobstatus == 0) {
                                                                                                            return; //Job has not completed
                                                                                                        } else {
                                                                                                            clearInterval(enableVirtualRouterProviderIntervalID);

                                                                                                            if (result.jobstatus == 1) { //Virtual Router Provider has been enabled successfully
                                                                                                                advZoneConfiguredVirtualRouterCount++;

                                                                                                                if (advZoneConfiguredVirtualRouterCount == args.data.returnedPhysicalNetworks.length) { //not call next stepFns.addXXX() until virtualRouter of all physical networks get configured
                                                                                                                    if (args.data.zone.sgEnabled != true) { //Advanced SG-disabled zone
                                                                                                                        stepFns.addPod({
                                                                                                                            data: args.data
                                                                                                                        });
                                                                                                                    } else { //args.data.zone.sgEnabled    == true  //Advanced SG-enabled zone
                                                                                                                        stepFns.addGuestNetwork({
                                                                                                                            data: args.data
                                                                                                                        });
                                                                                                                    }
                                                                                                                }
                                                                                                            } else if (result.jobstatus == 2) {
                                                                                                                alert("failed to enable Virtual Router Provider. Error: " + _s(result.jobresult.errortext));
                                                                                                            }
                                                                                                        }
                                                                                                    },
                                                                                                    error: function (XMLHttpResponse) {
                                                                                                        var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                                        alert("updateNetworkServiceProvider failed. Error: " + errorMsg);
                                                                                                    }
                                                                                                });
                                                                                            }, g_queryAsyncJobResultInterval);
                                                                                        }
                                                                                    });
                                                                                } else if (result.jobstatus == 2) {
                                                                                    alert("configureVirtualRouterElement failed. Error: " + _s(result.jobresult.errortext));
                                                                                }
                                                                            }
                                                                        },
                                                                        error: function (XMLHttpResponse) {
                                                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                            alert("configureVirtualRouterElement failed. Error: " + errorMsg);
                                                                        }
                                                                    });
                                                                }, g_queryAsyncJobResultInterval);
                                                            }
                                                        });
                                                        // ***** Virtual Router ***** (end) *****

                                                        if (args.data.zone.sgEnabled != true) { //Advanced SG-disabled zone
                                                            // ***** VPC Virtual Router ***** (begin) *****
                                                            var vpcVirtualRouterProviderId;
                                                            $.ajax({
                                                                url: createURL("listNetworkServiceProviders&name=VpcVirtualRouter&physicalNetworkId=" + thisPhysicalNetwork.id),
                                                                dataType: "json",
                                                                async: false,
                                                                success: function (json) {
                                                                    var items = json.listnetworkserviceprovidersresponse.networkserviceprovider;
                                                                    if (items != null && items.length > 0) {
                                                                        vpcVirtualRouterProviderId = items[0].id;
                                                                    }
                                                                }
                                                            });
                                                            if (vpcVirtualRouterProviderId == null) {
                                                                alert("error: listNetworkServiceProviders API doesn't return VpcVirtualRouter provider ID");
                                                                return;
                                                            }

                                                            var vpcVirtualRouterElementId;
                                                            $.ajax({
                                                                url: createURL("listVirtualRouterElements&nspid=" + vpcVirtualRouterProviderId),
                                                                dataType: "json",
                                                                async: false,
                                                                success: function (json) {
                                                                    var items = json.listvirtualrouterelementsresponse.virtualrouterelement;
                                                                    if (items != null && items.length > 0) {
                                                                        vpcVirtualRouterElementId = items[0].id;
                                                                    }
                                                                }
                                                            });
                                                            if (vpcVirtualRouterElementId == null) {
                                                                alert("error: listVirtualRouterElements API doesn't return VPC Virtual Router Element Id");
                                                                return;
                                                            }

                                                            $.ajax({
                                                                url: createURL("configureVirtualRouterElement&enabled=true&id=" + vpcVirtualRouterElementId),
                                                                dataType: "json",
                                                                async: false,
                                                                success: function (json) {
                                                                    var jobId = json.configurevirtualrouterelementresponse.jobid;
                                                                    var enableVpcVirtualRouterElementIntervalID = setInterval(function () {
                                                                        $.ajax({
                                                                            url: createURL("queryAsyncJobResult&jobId=" + jobId),
                                                                            dataType: "json",
                                                                            success: function (json) {
                                                                                var result = json.queryasyncjobresultresponse;
                                                                                if (result.jobstatus == 0) {
                                                                                    return; //Job has not completed
                                                                                } else {
                                                                                    clearInterval(enableVpcVirtualRouterElementIntervalID);

                                                                                    if (result.jobstatus == 1) { //configureVirtualRouterElement succeeded
                                                                                        $.ajax({
                                                                                            url: createURL("updateNetworkServiceProvider&state=Enabled&id=" + vpcVirtualRouterProviderId),
                                                                                            dataType: "json",
                                                                                            async: false,
                                                                                            success: function (json) {
                                                                                                var jobId = json.updatenetworkserviceproviderresponse.jobid;
                                                                                                var enableVpcVirtualRouterProviderIntervalID = setInterval(function () {
                                                                                                    $.ajax({
                                                                                                        url: createURL("queryAsyncJobResult&jobId=" + jobId),
                                                                                                        dataType: "json",
                                                                                                        success: function (json) {
                                                                                                            var result = json.queryasyncjobresultresponse;
                                                                                                            if (result.jobstatus == 0) {
                                                                                                                return; //Job has not completed
                                                                                                            } else {
                                                                                                                clearInterval(enableVpcVirtualRouterProviderIntervalID);

                                                                                                                if (result.jobstatus == 1) { //VPC Virtual Router has been enabled successfully
                                                                                                                    //don't need to do anything here
                                                                                                                } else if (result.jobstatus == 2) {
                                                                                                                    alert("failed to enable VPC Virtual Router Provider. Error: " + _s(result.jobresult.errortext));
                                                                                                                }
                                                                                                            }
                                                                                                        },
                                                                                                        error: function (XMLHttpResponse) {
                                                                                                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                                            alert("failed to enable VPC Virtual Router Provider. Error: " + errorMsg);
                                                                                                        }
                                                                                                    });
                                                                                                }, g_queryAsyncJobResultInterval);
                                                                                            }
                                                                                        });
                                                                                    } else if (result.jobstatus == 2) {
                                                                                        alert("configureVirtualRouterElement failed. Error: " + _s(result.jobresult.errortext));
                                                                                    }
                                                                                }
                                                                            },
                                                                            error: function (XMLHttpResponse) {
                                                                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                alert("configureVirtualRouterElement failed. Error: " + errorMsg);
                                                                            }
                                                                        });
                                                                    }, g_queryAsyncJobResultInterval);
                                                                }
                                                            });
                                                            // ***** VPC Virtual Router ***** (end) *****
                                                        } else { //args.data.zone.sgEnabled == true  //Advanced SG-enabled zone
                                                            message(_l('message.enabling.security.group.provider'));

                                                            // get network service provider ID of Security Group
                                                            var securityGroupProviderId;
                                                            $.ajax({
                                                                url: createURL("listNetworkServiceProviders&name=SecurityGroupProvider&physicalNetworkId=" + thisPhysicalNetwork.id),
                                                                dataType: "json",
                                                                async: false,
                                                                success: function (json) {
                                                                    var items = json.listnetworkserviceprovidersresponse.networkserviceprovider;
                                                                    if (items != null && items.length > 0) {
                                                                        securityGroupProviderId = items[0].id;
                                                                    }
                                                                }
                                                            });
                                                            if (securityGroupProviderId == null) {
                                                                alert("error: listNetworkServiceProviders API doesn't return security group provider ID");
                                                                return;
                                                            }

                                                            $.ajax({
                                                                url: createURL("updateNetworkServiceProvider&state=Enabled&id=" + securityGroupProviderId),
                                                                dataType: "json",
                                                                async: false,
                                                                success: function (json) {
                                                                    var enableSecurityGroupProviderIntervalID = setInterval(function () {
                                                                        $.ajax({
                                                                            url: createURL("queryAsyncJobResult&jobId=" + json.updatenetworkserviceproviderresponse.jobid),
                                                                            dataType: "json",
                                                                            success: function (json) {
                                                                                var result = json.queryasyncjobresultresponse;
                                                                                if (result.jobstatus == 0) {
                                                                                    return; //Job has not completed
                                                                                } else {
                                                                                    clearInterval(enableSecurityGroupProviderIntervalID);

                                                                                    if (result.jobstatus == 1) { //Security group provider has been enabled successfully
                                                                                        //don't need to do anything here
                                                                                    } else if (result.jobstatus == 2) {
                                                                                        alert("failed to enable security group provider. Error: " + _s(result.jobresult.errortext));
                                                                                    }
                                                                                }
                                                                            },
                                                                            error: function (XMLHttpResponse) {
                                                                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                                                alert("failed to enable security group provider. Error: " + errorMsg);
                                                                            }
                                                                        });
                                                                    }, g_queryAsyncJobResultInterval);
                                                                }
                                                            });
                                                        }
                                                    } else if (result.jobstatus == 2) {
                                                        alert("failed to enable physical network. Error: " + _s(result.jobresult.errortext));
                                                    }
                                                }
                                            },
                                            error: function (XMLHttpResponse) {
                                                alert("failed to enable physical network. Error: " + parseXMLHttpResponse(XMLHttpResponse));
                                            }
                                        });
                                    }, g_queryAsyncJobResultInterval);
                                }
                            });
                        });
                    }
                },

                addGuestNetwork: function (args) { //create a guest network for Basic zone or Advanced zone with SG
                    message(_l('message.creating.guest.network'));

                    var data = {
                        zoneid: args.data.returnedZone.id,
                        name: 'defaultGuestNetwork',
                        displaytext: 'defaultGuestNetwork',
                        networkofferingid: args.data.zone.networkOfferingId
                    };

                    //Advanced zone with SG
                    if (args.data.zone.networkType == "Advanced" && args.data.zone.sgEnabled == true) {
                        $.extend(data, {
                            gateway: args.data.guestTraffic.guestGateway,
                            netmask: args.data.guestTraffic.guestNetmask,
                            startip: args.data.guestTraffic.guestStartIp,
                            vlan: args.data.guestTraffic.vlanId
                        });
                        if (args.data.guestTraffic.guestEndIp != null && args.data.guestTraffic.guestEndIp.length > 0) {
                            $.extend(data, {
                                endip: args.data.guestTraffic.guestEndIp
                            });
                        }
                    }

                    $.ajax({
                        url: createURL('createNetwork'),
                        data: data,
                        async: false,
                        success: function (json) {
                            //basic zone has only one physical network => addPod() will be called only once => so don't need to double-check before calling addPod()
                            stepFns.addPod({
                                data: $.extend(args.data, {
                                    returnedGuestNetwork: json.createnetworkresponse.network
                                })
                            });
                        },
                        error: function (XMLHttpResponse) {
                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                            alert("failed to create a guest network for basic zone. Error: " + errorMsg);
                        }
                    });
                },

                addPod: function (args) {
                    message(_l('message.creating.pod'));

                    var array3 = [];
                    array3.push("&zoneId=" + args.data.returnedZone.id);
                    array3.push("&name=" + todb(args.data.pod.name));
                    array3.push("&gateway=" + todb(args.data.pod.reservedSystemGateway));
                    array3.push("&netmask=" + todb(args.data.pod.reservedSystemNetmask));
                    array3.push("&startIp=" + todb(args.data.pod.reservedSystemStartIp));

                    var endip = args.data.pod.reservedSystemEndIp; //optional
                    if (endip != null && endip.length > 0)
                        array3.push("&endIp=" + todb(endip));

                    $.ajax({
                        url: createURL("createPod" + array3.join("")),
                        dataType: "json",
                        async: false,
                        success: function (json) {
                            stepFns.configurePublicTraffic({
                                data: $.extend(args.data, {
                                    returnedPod: json.createpodresponse.pod
                                })
                            });
                        },
                        error: function (XMLHttpResponse) {
                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                            error('addPod', errorMsg, {
                                fn: 'addPod',
                                args: args
                            });
                        }
                    });
                },

                configurePublicTraffic: function (args) {
                    if ((args.data.zone.networkType == "Basic" && (selectedNetworkOfferingHavingSG == true && selectedNetworkOfferingHavingEIP == true && selectedNetworkOfferingHavingELB == true)) || (args.data.zone.networkType == "Advanced" && args.data.zone.sgEnabled != true)) {

                        message(_l('message.configuring.public.traffic'));

                        var stopNow = false;

                        $(args.data.publicTraffic).each(function () {
                            var thisPublicVlanIpRange = this;

                            //check whether the VlanIpRange exists or not (begin)
                            var isExisting = false;
                            $(returnedPublicVlanIpRanges).each(function () {
                                if (this.vlan == thisPublicVlanIpRange.vlanid && this.startip == thisPublicVlanIpRange.startip && this.netmask == thisPublicVlanIpRange.netmask && this.gateway == thisPublicVlanIpRange.gateway) {
                                    isExisting = true;
                                    return false; //break each loop
                                }
                            });
                            if (isExisting == true)
                                return; //skip current item to next item (continue each loop)

                            //check whether the VlanIpRange exists or not (end)

                            var array1 = [];
                            array1.push("&zoneId=" + args.data.returnedZone.id);

                            if (this.vlanid != null && this.vlanid.length > 0)
                                array1.push("&vlan=" + todb(this.vlanid));
                            else
                                array1.push("&vlan=untagged");

                            array1.push("&gateway=" + this.gateway);
                            array1.push("&netmask=" + this.netmask);
                            array1.push("&startip=" + this.startip);
                            if (this.endip != null && this.endip.length > 0)
                                array1.push("&endip=" + this.endip);

                            if (args.data.zone.networkType == "Basic") {
                                array1.push("&forVirtualNetwork=true");
                            } else if (args.data.zone.networkType == "Advanced") {
                                if (args.data.zone.sgEnabled != true) {
                                    array1.push("&forVirtualNetwork=true");
                                } else { //args.data.zone.sgEnabled    == true
                                    array1.push("&forVirtualNetwork=false");
                                }
                            }

                            $.ajax({
                                url: createURL("createVlanIpRange" + array1.join("")),
                                dataType: "json",
                                async: false,
                                success: function (json) {
                                    var item = json.createvlaniprangeresponse.vlan;
                                    returnedPublicVlanIpRanges.push(item);
                                },
                                error: function (XMLHttpResponse) {
                                    var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                    error('configurePublicTraffic', errorMsg, {
                                        fn: 'configurePublicTraffic',
                                        args: args
                                    });
                                    stopNow = true;
                                }
                            });

                            if (stopNow == true)
                                return false; //break each loop, don't create next VlanIpRange
                        });

                        if (stopNow == true)
                            return; //stop the whole process

                        stepFns.configureStorageTraffic({
                            data: $.extend(args.data, {
                                returnedPublicTraffic: returnedPublicVlanIpRanges
                            })
                        });
                    } else if (args.data.zone.networkType == "Advanced" && args.data.zone.sgEnabled == true) { // Advanced SG-enabled zone doesn't have public traffic type
                        stepFns.configureStorageTraffic({
                            data: args.data
                        });
                    } else { //basic zone without public traffic type , skip to next step
                        if (data.physicalNetworks && $.inArray('storage', data.physicalNetworks[0].trafficTypes) > -1) {
                            stepFns.configureStorageTraffic({
                                data: args.data
                            });
                        } else {
                            stepFns.configureGuestTraffic({
                                data: args.data
                            });
                        }
                    }
                },

                configureStorageTraffic: function (args) {
                    var complete = function (data) {
                        stepFns.configureGuestTraffic({
                            data: $.extend(args.data, data)
                        });
                    };

                    var targetNetwork = $.grep(args.data.physicalNetworks, function (net) {
                        return $.inArray('storage', net.trafficTypes) > -1;
                    });

                    if (!targetNetwork.length) {
                        return complete({});
                    }

                    message(_l('message.configuring.storage.traffic'));

                    var storageIPRanges = args.data.storageTraffic;
                    var tasks = [];
                    var taskTimer;

                    $(storageIPRanges).each(function () {
                        var item = this;
                        if ('vlan' in item && (item.vlan == null || item.vlan.length == 0))
                            delete item.vlan;
                        $.ajax({
                            url: createURL('createStorageNetworkIpRange'),
                            data: $.extend(true, {}, item, {
                                zoneid: args.data.returnedZone.id,
                                podid: args.data.returnedPod.id
                            }),
                            success: function (json) {
                                tasks.push({
                                    jobid: json.createstoragenetworkiprangeresponse.jobid,
                                    complete: false
                                });
                            },
                            error: function (json) {
                                tasks.push({
                                    error: true,
                                    message: parseXMLHttpResponse(json)
                                });
                            }
                        });
                    });

                    taskTimer = setInterval(function () {
                        var completedTasks = $.grep(tasks, function (task) {
                            return task.complete || task.error;
                        });

                        var errorTasks = $.grep(tasks, function (task) {
                            return task.error;
                        });

                        if (completedTasks.length == storageIPRanges.length) {
                            clearInterval(taskTimer);

                            if (errorTasks.length) {
                                return error('configureStorageTraffic', errorTasks[0].message, {
                                    fn: 'configureStorageTraffic',
                                    args: args
                                });
                            }

                            return complete({});
                        }

                        if (tasks.length == storageIPRanges.length) {
                            $(tasks).each(function () {
                                var task = this;

                                if (task.error) return true;

                                pollAsyncJobResult({
                                    _custom: {
                                        jobId: task.jobid
                                    },
                                    complete: function () {
                                        task.complete = true;
                                    },
                                    error: function (args) {
                                        task.error = true;
                                        task.message = args.message;
                                    }
                                });

                                return true;
                            });
                        }

                        return true;
                    }, 1000);

                    return true;
                },

                configureGuestTraffic: function (args) {
                    if (skipGuestTrafficStep == true) {
                        stepFns.addCluster({
                            data: args.data
                        });
                        return;
                    }

                    message(_l('message.configuring.guest.traffic'));

                    if (args.data.returnedZone.networktype == "Basic") { //create an VlanIpRange for guest network in basic zone
                        var array1 = [];
                        array1.push("&podid=" + args.data.returnedPod.id);
                        array1.push("&networkid=" + args.data.returnedGuestNetwork.id);
                        array1.push("&gateway=" + args.data.guestTraffic.guestGateway);
                        array1.push("&netmask=" + args.data.guestTraffic.guestNetmask);
                        array1.push("&startip=" + args.data.guestTraffic.guestStartIp);
                        if (args.data.guestTraffic.guestEndIp != null && args.data.guestTraffic.guestEndIp.length > 0)
                            array1.push("&endip=" + args.data.guestTraffic.guestEndIp);
                        array1.push("&forVirtualNetwork=false"); //indicates this new IP range is for guest network, not public network

                        $.ajax({
                            url: createURL("createVlanIpRange" + array1.join("")),
                            dataType: "json",
                            success: function (json) {
                                args.data.returnedGuestNetwork.returnedVlanIpRange = json.createvlaniprangeresponse.vlan;
                                stepFns.addCluster({
                                    data: args.data
                                });
                            },
                            error: function (XMLHttpResponse) {
                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                error('configureGuestTraffic', errorMsg, {
                                    fn: 'configureGuestTraffic',
                                    args: args
                                });
                            }
                        });
                    } else if (args.data.returnedZone.networktype == "Advanced") { //update VLAN in physical network(s) in advanced zone
                        var physicalNetworksHavingGuestIncludingVlan = [];
                        $(args.data.physicalNetworks).each(function () {
                            if (this.guestConfiguration != null && this.guestConfiguration.vlanRangeStart != null && this.guestConfiguration.vlanRangeStart.length > 0) {
                                physicalNetworksHavingGuestIncludingVlan.push(this);
                            }
                        });

                        if (physicalNetworksHavingGuestIncludingVlan.length == 0) {
                            stepFns.addCluster({
                                data: args.data
                            });
                        } else {
                            var updatedCount = 0;
                            $(physicalNetworksHavingGuestIncludingVlan).each(function () {
                                var vlan;
                                if (this.guestConfiguration.vlanRangeEnd == null || this.guestConfiguration.vlanRangeEnd.length == 0)
                                    vlan = this.guestConfiguration.vlanRangeStart;
                                else
                                    vlan = this.guestConfiguration.vlanRangeStart + "-" + this.guestConfiguration.vlanRangeEnd;

                                var originalId = this.id;
                                var returnedId;
                                $(args.data.returnedPhysicalNetworks).each(function () {
                                    if (this.originalId == originalId) {
                                        returnedId = this.id;
                                        return false; //break the loop
                                    }
                                });

                                $.ajax({
                                    url: createURL("updatePhysicalNetwork&id=" + returnedId + "&vlan=" + todb(vlan)),
                                    dataType: "json",
                                    success: function (json) {
                                        var jobId = json.updatephysicalnetworkresponse.jobid;
                                        var updatePhysicalNetworkVlanIntervalID = setInterval(function () {
                                            $.ajax({
                                                url: createURL("queryAsyncJobResult&jobid=" + jobId),
                                                dataType: "json",
                                                success: function (json) {
                                                    var result = json.queryasyncjobresultresponse;
                                                    if (result.jobstatus == 0) {
                                                        return;
                                                    } else {
                                                        clearInterval(updatePhysicalNetworkVlanIntervalID);

                                                        if (result.jobstatus == 1) {
                                                            updatedCount++;
                                                            if (updatedCount == physicalNetworksHavingGuestIncludingVlan.length) {
                                                                stepFns.addCluster({
                                                                    data: args.data
                                                                });
                                                            }
                                                        } else if (result.jobstatus == 2) {
                                                            alert("error: " + _s(result.jobresult.errortext));
                                                            error('configureGuestTraffic', result.jobresult.errortext, {
                                                                fn: 'configureGuestTraffic',
                                                                args: args
                                                            });

                                                        }
                                                    }
                                                },
                                                error: function (XMLHttpResponse) {
                                                    var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                                    error('configureGuestTraffic', errorMsg, {
                                                        fn: 'configureGuestTraffic',
                                                        args: args
                                                    });
                                                }
                                            });
                                        }, g_queryAsyncJobResultInterval);
                                    }
                                });
                            });
                        }
                    }
                },

                addCluster: function (args) {
                    message(_l('message.creating.cluster'));

                    // Have cluster use zone's hypervisor
                    args.data.cluster.hypervisor = args.data.zone.hypervisor ?
                        args.data.zone.hypervisor : args.data.cluster.hypervisor;

                    var array1 = [];
                    array1.push("&zoneId=" + args.data.returnedZone.id);
                    array1.push("&hypervisor=" + args.data.cluster.hypervisor);

                    var clusterType = "CloudManaged";
                    array1.push("&clustertype=" + clusterType);

                    array1.push("&podId=" + args.data.returnedPod.id);

                    var clusterName = args.data.cluster.name;
                    array1.push("&clustername=" + todb(clusterName));

                    $.ajax({
                        url: createURL("addCluster" + array1.join("")),
                        dataType: "json",
                        type: "POST",
                        success: function (json) {
                            stepFns.addHost({
                                data: $.extend(args.data, {
                                    returnedCluster: json.addclusterresponse.cluster[0]
                                })
                            });
                        },
                        error: function (XMLHttpResponse) {
                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                            error('addCluster', errorMsg, {
                                fn: 'addCluster',
                                args: args
                            });
                        }
                    });
                },

                addHost: function (args) {
                    message(_l('message.adding.host'));

                    var data = {
                        zoneid: args.data.returnedZone.id,
                        podid: args.data.returnedPod.id,
                        clusterid: args.data.returnedCluster.id,
                        hypervisor: args.data.returnedCluster.hypervisortype,
                        clustertype: args.data.returnedCluster.clustertype,
                        hosttags: args.data.host.hosttags,
                        username: args.data.host.username,
                        password: args.data.host.password
                    };

                    var hostname = args.data.host.hostname;
                    var url;
                    if (hostname.indexOf("http://") == -1) {
                        url = "http://" + hostname;
                    } else {
                        url = hostname;
                    }
                    $.extend(data, {
                        url: url
                    });

                    if (args.data.cluster.hypervisor == "Ovm") {
                        $.extend(data, {
                            agentusername: args.data.host.agentUsername,
                            agentpassword: args.data.host.agentPassword
                        });
                    }

                    var addHostAjax = function () {
                        $.ajax({
                            url: createURL("addHost"),
                            type: "POST",
                            data: data,
                            success: function (json) {
                                stepFns.addPrimaryStorage({
                                    data: $.extend(args.data, {
                                        returnedHost: json.addhostresponse.host[0]
                                    })
                                });
                            },
                            error: function (XMLHttpResponse) {
                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                error('addHost', errorMsg, {
                                    fn: 'addHost',
                                    args: args
                                });
                            }
                        });
                    };

                    if (args.data.zone.localstorageenabledforsystemvm == 'on') {
                        $.ajax({
                            url: createURL("updateConfiguration&name=system.vm.use.local.storage&value=true&zoneid=" + args.data.returnedZone.id),
                            dataType: "json",
                            success: function (json) {
                                addHostAjax();
                            },
                            error: function (XMLHttpResponse) {
                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                error('addHost', errorMsg, {
                                    fn: 'addHost',
                                    args: args
                                });
                            }
                        });
                    } else {
                        addHostAjax();
                    }
                },

                addPrimaryStorage: function (args) {
                    if (args.data.zone.localstorageenabled == 'on' && args.data.zone.localstorageenabledforsystemvm == 'on') { //use local storage, don't need primary storage. So, skip this step.
                        stepFns.addSecondaryStorage({
                            data: args.data
                        });
                        return;
                    }

                    message(_l('message.creating.primary.storage'));

                    var array1 = [];
                    array1.push("&zoneid=" + args.data.returnedZone.id);
                    array1.push("&podId=" + args.data.returnedPod.id);
                    array1.push("&clusterid=" + args.data.returnedCluster.id);
                    array1.push("&name=" + todb(args.data.primaryStorage.name));
                    array1.push("&scope=" + todb(args.data.primaryStorage.scope));

                    //zone-wide-primary-storage is supported only for KVM
                    if (args.data.primaryStorage.scope == "zone") { //hypervisor type of the hosts in zone that will be attached to this storage pool. KVM supported as of now.
                        if (args.data.cluster.hypervisor != undefined) {
                            array1.push("&hypervisor=" + todb(args.data.cluster.hypervisor));
                        } else if (args.data.returnedCluster.hypervisortype != undefined) {
                            array1.push("&hypervisor=" + todb(args.data.returnedCluster.hypervisortype));
                        } else {
                            cloudStack.dialog.notice({
                                message: "Error: args.data.cluster.hypervisor is undefined. So is args.data.returnedCluster.hypervisortype (zone-wide-primary-storage)"
                            });
                        }
                    }

                    var server = args.data.primaryStorage.server;
                    var url = null;
                    if (args.data.primaryStorage.protocol == "nfs") {
                        var path = args.data.primaryStorage.path;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        url = nfsURL(server, path);
                    } else if (args.data.primaryStorage.protocol == "SMB") {
                        var path = args.data.primaryStorage.path;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        url = smbURL(server, path);
                        array1.push("&details[0].user=" + args.data.primaryStorage.smbUsername);
                        array1.push("&details[1].password=" + todb(args.data.primaryStorage.smbPassword));
                        array1.push("&details[2].domain=" + args.data.primaryStorage.smbDomain);
                    } else if (args.data.primaryStorage.protocol == "PreSetup") {
                        var path = args.data.primaryStorage.path;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        url = presetupURL(server, path);
                    } else if (args.data.primaryStorage.protocol == "ocfs2") {
                        var path = args.data.primaryStorage.path;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        url = ocfs2URL(server, path);
                    } else if (args.data.primaryStorage.protocol == "SharedMountPoint") {
                        var path = args.data.primaryStorage.path;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        url = SharedMountPointURL(server, path);
                    } else if (args.data.primaryStorage.protocol == "clvm") {
                        var vg = args.data.primaryStorage.volumegroup;
                        if (vg.substring(0, 1) != "/")
                            vg = "/" + vg;
                        url = clvmURL(vg);
                    } else if (args.data.primaryStorage.protocol == "rbd") {
                        var rbdmonitor = args.data.primaryStorage.rbdmonitor;
                        var rbdpool = args.data.primaryStorage.rbdpool;
                        var rbdid = args.data.primaryStorage.rbdid;
                        var rbdsecret = args.data.primaryStorage.rbdsecret;
                        url = rbdURL(rbdmonitor, rbdpool, rbdid, rbdsecret);
                    } else if (args.data.primaryStorage.protocol == "vmfs") {
                        var path = args.data.primaryStorage.vCenterDataCenter;
                        if (path.substring(0, 1) != "/")
                            path = "/" + path;
                        path += "/" + args.data.primaryStorage.vCenterDataStore;
                        url = vmfsURL("dummy", path);
                    } else {
                        var iqn = args.data.primaryStorage.iqn;
                        if (iqn.substring(0, 1) != "/")
                            iqn = "/" + iqn;
                        var lun = args.data.primaryStorage.lun;
                        url = iscsiURL(server, iqn, lun);
                    }
                    array1.push("&url=" + todb(url));

                    if (args.data.primaryStorage.storageTags != null && args.data.primaryStorage.storageTags.length > 0)
                        array1.push("&tags=" + todb(args.data.primaryStorage.storageTags));

                    $.ajax({
                        url: createURL("createStoragePool" + array1.join("")),
                        dataType: "json",
                        success: function (json) {
                            stepFns.addSecondaryStorage({
                                data: $.extend(args.data, {
                                    returnedPrimaryStorage: json.createstoragepoolresponse.storagepool
                                })
                            });
                        },
                        error: function (XMLHttpResponse) {
                            var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                            error('addPrimaryStorage', errorMsg, {
                                fn: 'addPrimaryStorage',
                                args: args
                            });
                        }
                    });
                },

                addSecondaryStorage: function (args) {
                    if (args.data.secondaryStorage.provider == '') {
                        complete({
                            data: args.data
                        });
                        return; //skip addSecondaryStorage if provider dropdown is blank
                    }


                    message(_l('message.creating.secondary.storage'));

                    var data = {};
                    if (args.data.secondaryStorage.name != null && args.data.secondaryStorage.name.length > 0) {
                        $.extend(data, {
                            name: args.data.secondaryStorage.name
                        });
                    }

                    if (args.data.secondaryStorage.provider == 'NFS') {
                        var nfs_server = args.data.secondaryStorage.nfsServer;
                        var path = args.data.secondaryStorage.path;
                        var url = nfsURL(nfs_server, path);

                        $.extend(data, {
                            provider: args.data.secondaryStorage.provider,
                            zoneid: args.data.returnedZone.id,
                            url: url
                        });

                        $.ajax({
                            url: createURL('addImageStore'),
                            data: data,
                            success: function (json) {
                                complete({
                                    data: $.extend(args.data, {
                                        returnedSecondaryStorage: json.addimagestoreresponse.secondarystorage
                                    })
                                });
                            },
                            error: function (XMLHttpResponse) {
                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                error('addSecondaryStorage', errorMsg, {
                                    fn: 'addSecondaryStorage',
                                    args: args
                                });
                            }
                        });
                    } else if (args.data.secondaryStorage.provider == 'SMB') {
                        var nfs_server = args.data.secondaryStorage.nfsServer;
                        var path = args.data.secondaryStorage.path;
                        var url = smbURL(nfs_server, path);

                        $.extend(data, {
                            provider: args.data.secondaryStorage.provider,
                            zoneid: args.data.returnedZone.id,
                            url: url,
                            'details[0].key': 'user',
                            'details[0].value': args.data.secondaryStorage.smbUsername,
                            'details[1].key': 'password',
                            'details[1].value': args.data.secondaryStorage.smbPassword,
                            'details[2].key': 'domain',
                            'details[2].value': args.data.secondaryStorage.smbDomain
                        });

                        $.ajax({
                            url: createURL('addImageStore'),
                            data: data,
                            success: function (json) {
                                complete({
                                    data: $.extend(args.data, {
                                        returnedSecondaryStorage: json.addimagestoreresponse.secondarystorage
                                    })
                                });
                            },
                            error: function (XMLHttpResponse) {
                                var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                                error('addSecondaryStorage', errorMsg, {
                                    fn: 'addSecondaryStorage',
                                    args: args
                                });
                            }
                        });
                    }
                }
            };

            var complete = function (args) {
                message(_l('message.Zone.creation.complete'));
                success(args);
            };

            if (startFn) {
                stepFns[startFn.fn]({
                    data: $.extend(startFn.args.data, data)
                });
            } else {
                stepFns.addZone({});
            }
        },

        enableZoneAction: function (args) {
            $.ajax({
                url: createURL("updateZone&allocationstate=Enabled&id=" + args.launchData.returnedZone.id),
                dataType: "json",
                success: function (json) {
                    args.formData.returnedZone = json.updatezoneresponse.zone;
                    args.response.success();
                }
            });
        }
    };
}(cloudStack, jQuery));
