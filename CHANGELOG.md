# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

[Tags on this repository](https://github.com/AndreyVMarkelov/jira-prometheus-exporter/releases)

## [Unreleased]

- Standardize product case to Jira to align with [Atlassian branding changes](https://community.atlassian.com/t5/Feedback-Forum-articles/A-new-look-for-Atlassian/ba-p/638077)

## [1.0.33-jira8] (v8.x - 8.7.x)
- Fix Fogue dependency

## [1.0.32-jira8] (v8.6.1 - 8.6.x)

- New metrics: jira_application_link_status_gauge and jira_application_link_count_gauge
- Support Jira 8.6.x

## [1.0.31-jira8] (v8.x - 8.4.x)

- Fixed license metrics (added application name label)
- Support Jira 8.4.x

## [1.0.31-jira7] (v7.3.x - 7.13.x)

- Fixed license metrics (added application name label)

## [1.0.30-jira8] (v8.x - 8.3.x)

- jira_issue_index_reads_gauge (Index Reads Count)
- jira_issue_index_writes_gauge (Index Writes Count)
- jira_total_workflows_gauge (Workflows Gauge)
- jira_total_customfields_gauge (Custom Fields Gauge)
- jira_total_groups_gauge (Groups Gauge)
- jira_total_projects_gauge (Projects Gauge)
- jira_total_attachments_gauge (Attachments Gauge)
- jira_total_versions_gauge (Versions Gauge)
- jira_total_filters_gauge (Filters Gauge)
- jira_total_components_gauge (Components Gauge)

## [1.0.30-jira7] (v7.3.x - 7.13.x)

- jira_issue_index_reads_gauge (Index Reads Count)
- jira_issue_index_writes_gauge (Index Writes Count)
- jira_total_workflows_gauge (Workflows Gauge)
- jira_total_customfields_gauge (Custom Fields Gauge)
- jira_total_groups_gauge (Groups Gauge)
- jira_total_projects_gauge (Projects Gauge)

## [1.0.29-jira8] (v8.x - 8.2.x)

- Fixed high CPU
- Fixed missed license metrics
- Added new metric for DC: jira_total_cluster_nodes_gauge
- Added new metric for DC: jira_active_cluster_nodes_gauge
- Added new metric for DC: jira_cluster_heartbeat_counter

## [1.0.29-jira7] (v7.3.x - 7.13.x)

- Fixed high CPU
- Fixed missed license metrics
- Added new metric for DC: jira_total_cluster_nodes_gauge
- Added new metric for DC: jira_active_cluster_nodes_gauge
- Added new metric for DC: jira_cluster_heartbeat_counter

## [1.0.28] (v7.3.x-8.2.x)

- Fixed high CPU
- Fixed missed license metrics
- Added new metric for DC: jira_total_cluster_nodes_gauge
- Added new metric for DC: jira_active_cluster_nodes_gauge
- Added new metric for DC: jira_cluster_heartbeat_counter
