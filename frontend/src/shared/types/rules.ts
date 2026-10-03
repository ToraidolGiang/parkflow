export type RuleType = 'alert' | 'routing'
export type RuleMetric = 'forecast_pct' | 'current_pct' | 'inflow_15m'
export type RuleOperator = '>=' | '>' | '<=' | '<'

export type RuleDraft = {
  name: string
  type: RuleType
  scope: string
  from: string
  to: string
  metric: RuleMetric
  op: RuleOperator
  value: number
  action: string
}

export type Rule = RuleDraft & { id: number; createdAt: string; enabled: boolean }
export type Alert = { id: number; ruleId: number; at: string; lotId: string; message: string }
export type ParsedRule = { rule: RuleDraft; explanation: string; source: string }
