import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  FinanceScenarioBaselineSummary,
  FinanceScenarioControl,
  FinanceScenarioDashboardSnapshot,
  FinanceScenarioMarketSummary,
  FinanceScenarioModelMeta,
  FinanceScenarioMonthPath,
  FinanceScenarioPricingSummary,
  FinanceScenarioRanking,
  FinanceScenarioRecommendationContext,
  FinanceScenarioSummary
} from '../models/finance-scenario.model';
import { FINANCE_SCENARIO_FALLBACK } from '../data/finance-scenario.fallback';

interface ScenarioAssetSummary {
  generated_at?: string;
  assumption?: string;
  baseline_summary?: Record<string, unknown>;
  market_summary?: Record<string, unknown>;
  pricing_summary?: Record<string, unknown>;
  recommendation_context?: Record<string, unknown>;
  model?: Record<string, unknown>;
  scenario_controls?: Array<Record<string, unknown>>;
  scenario_rankings?: Array<Record<string, unknown>>;
  recommended_scenario?: Record<string, unknown>;
  recommended_monthly_path?: Array<Record<string, unknown>>;
  dashboard_snapshot?: Record<string, unknown> | null;
}

@Injectable({
  providedIn: 'root'
})
export class FinanceScenarioService {
  private readonly apiUrl = `${environment.apiUrl}/finance/scenario-simulator`;
  private readonly fallbackSummaryUrl = 'assets/scenario-simulator/scenario_simulator_summary.json';

  constructor(private http: HttpClient) {}

  getSummary(): Observable<FinanceScenarioSummary> {
    return this.http.get<FinanceScenarioSummary>(`${this.apiUrl}/summary`).pipe(
      catchError(() =>
        this.http.get<ScenarioAssetSummary>(this.fallbackSummaryUrl).pipe(
          map((summary) => this.mapAssetSummary(summary)),
          catchError(() => of(FINANCE_SCENARIO_FALLBACK))
        )
      )
    );
  }

  private mapAssetSummary(summary: ScenarioAssetSummary): FinanceScenarioSummary {
    return {
      generatedAt: this.asString(summary.generated_at),
      assumption: this.asString(summary.assumption),
      baselineSummary: this.mapBaselineSummary(summary.baseline_summary),
      marketSummary: this.mapMarketSummary(summary.market_summary),
      pricingSummary: this.mapPricingSummary(summary.pricing_summary),
      recommendationContext: this.mapRecommendationContext(summary.recommendation_context),
      model: this.mapModel(summary.model),
      scenarioControls: (summary.scenario_controls ?? []).map((item) => this.mapControl(item)),
      scenarioRankings: (summary.scenario_rankings ?? []).map((item) => this.mapRanking(item)),
      recommendedScenario: this.mapRanking(summary.recommended_scenario ?? {}),
      recommendedMonthlyPath: (summary.recommended_monthly_path ?? []).map((item) => this.mapMonthPath(item)),
      dashboardSnapshot: this.mapDashboardSnapshot(summary.dashboard_snapshot)
    };
  }

  private mapBaselineSummary(raw: Record<string, unknown> | undefined): FinanceScenarioBaselineSummary {
    return {
      forecastFrom: this.asString(raw?.['forecast_from']),
      forecastTo: this.asString(raw?.['forecast_to']),
      monthsHorizon: this.asNumber(raw?.['months_horizon']),
      baselineIncomeTotal: this.asNumber(raw?.['baseline_income_total']),
      baselineExpensesTotal: this.asNumber(raw?.['baseline_expenses_total']),
      baselineProfitTotal: this.asNumber(raw?.['baseline_profit_total']),
      baselineMarginAvg: this.asNumber(raw?.['baseline_margin_avg']),
      baselineHighRiskMonths: this.asNumber(raw?.['baseline_high_risk_months'])
    };
  }

  private mapMarketSummary(raw: Record<string, unknown> | undefined): FinanceScenarioMarketSummary {
    return {
      globalMarketSignal: this.asNumber(raw?.['global_market_signal']),
      topOpportunityScore: this.asNumber(raw?.['top_opportunity_score']),
      weightedMarketRoi: this.asNumber(raw?.['weighted_market_roi']),
      weightedSalaryBoost: this.asNumber(raw?.['weighted_salary_boost']),
      topSkillCategory: this.asString(raw?.['top_skill_category']),
      topOpportunities: Array.isArray(raw?.['top_opportunities']) ? raw?.['top_opportunities'].map((item) => ({
        skillCategory: this.asString((item as Record<string, unknown>)['skill_category']),
        marketOpportunityScore: this.asNumber((item as Record<string, unknown>)['market_opportunity_score']),
        marketAvgRoi: this.asNumber((item as Record<string, unknown>)['market_avg_roi']),
        trendTotalViews: this.asNumber((item as Record<string, unknown>)['trend_total_views']),
        trendTotalEngagement: this.asNumber((item as Record<string, unknown>)['trend_total_engagement']),
        marketCourseCount: this.asNumber((item as Record<string, unknown>)['market_course_count'])
      })) : [],
      trackedSkills: this.asNumber(raw?.['tracked_skills']),
      totalScraperViews: this.asNumber(raw?.['total_scraper_views'])
    };
  }

  private mapPricingSummary(raw: Record<string, unknown> | undefined): FinanceScenarioPricingSummary {
    const recommendations = Array.isArray(raw?.['top_pricing_recommendations']) ? raw?.['top_pricing_recommendations'] : [];
    return {
      portfolioRevenueUpliftPct: this.asNumber(raw?.['portfolio_revenue_uplift_pct']),
      packsToIncreasePrice: this.asNumber(raw?.['packs_to_increase_price']),
      packsToDecreasePrice: this.asNumber(raw?.['packs_to_decrease_price']),
      packsToHoldPrice: this.asNumber(raw?.['packs_to_hold_price']),
      topPricingRecommendations: recommendations.map((item) => {
        const record = item as Record<string, unknown>;
        return {
          packId: this.asNumber(record['pack_id']),
          title: this.asString(record['title']),
          categoryName: this.asString(record['category_name']),
          priceAction: this.asString(record['price_action']),
          recommendedSalePrice: this.asNumber(record['recommended_sale_price']),
          revenueLiftPct: this.asNumber(record['revenue_lift_pct']),
          pricingRank: this.asNumber(record['pricing_rank'])
        };
      })
    };
  }

  private mapRecommendationContext(raw: Record<string, unknown> | undefined): FinanceScenarioRecommendationContext {
    const playbook = Array.isArray(raw?.['user_playbook']) ? raw?.['user_playbook'] : [];
    return {
      avgMarginUpliftPts: this.asNumber(raw?.['avg_margin_uplift_pts']),
      avgProfitUplift: this.asNumber(raw?.['avg_profit_uplift']),
      watchCategory: this.asString(raw?.['watch_category']),
      watchGrowthPct: this.asNumber(raw?.['watch_growth_pct']),
      monthsAtRiskBefore: this.asNumber(raw?.['months_at_risk_before']),
      monthsAtRiskAfter: this.asNumber(raw?.['months_at_risk_after']),
      bestGainWindow: this.asString(raw?.['best_gain_window']),
      userPlaybook: playbook.map((item) => {
        const record = item as Record<string, unknown>;
        return {
          step: this.asNullableNumber(record['step']),
          window: this.asNullableString(record['window']),
          priority: this.asNullableString(record['priority']),
          action: this.asNullableString(record['action']),
          why: this.asNullableString(record['why']),
          how: this.asNullableString(record['how']),
          expectedImpact: this.asNullableString(record['expected_impact']),
          successSignal: this.asNullableString(record['success_signal']),
          targetMetric: this.asNullableString(record['target_metric'])
        };
      })
    };
  }

  private mapModel(raw: Record<string, unknown> | undefined): FinanceScenarioModelMeta {
    return {
      algorithm: this.asString(raw?.['algorithm']),
      randomSeed: this.asNumber(raw?.['random_seed']),
      scenariosPerMonth: this.asNumber(raw?.['scenarios_per_month']),
      trainingRows: this.asNumber(raw?.['training_rows']),
      testRows: this.asNumber(raw?.['test_rows']),
      incomeMae: this.asNumber(raw?.['income_mae']),
      expenseMae: this.asNumber(raw?.['expense_mae']),
      incomeR2: this.asNumber(raw?.['income_r2']),
      expenseR2: this.asNumber(raw?.['expense_r2']),
      riskAccuracy: this.asNumber(raw?.['risk_accuracy'])
    };
  }

  private mapControl(raw: Record<string, unknown>): FinanceScenarioControl {
    return {
      name: this.asString(raw['name']),
      label: this.asString(raw['label']),
      min: this.asNumber(raw['min']),
      max: this.asNumber(raw['max']),
      unit: this.asString(raw['unit'])
    };
  }

  private mapRanking(raw: Record<string, unknown>): FinanceScenarioRanking {
    return {
      scenarioName: this.asString(raw['scenario_name']),
      scenarioSlug: this.asString(raw['scenario_slug']),
      description: this.asString(raw['description']),
      monthsHorizon: this.asNumber(raw['months_horizon']),
      projectedIncomeTotal: this.asNumber(raw['projected_income_total']),
      projectedExpensesTotal: this.asNumber(raw['projected_expenses_total']),
      projectedProfitTotal: this.asNumber(raw['projected_profit_total']),
      projectedMarginPct: this.asNumber(raw['projected_margin_pct']),
      profitUpliftPct: this.asNumber(raw['profit_uplift_pct']),
      marginUpliftPts: this.asNumber(raw['margin_uplift_pts']),
      incomeUpliftPct: this.asNumber(raw['income_uplift_pct']),
      expenseDeltaPct: this.asNumber(raw['expense_delta_pct']),
      riskLevel: this.asString(raw['risk_level']),
      highRiskMonths: this.asNumber(raw['high_risk_months']),
      mediumRiskMonths: this.asNumber(raw['medium_risk_months']),
      lowRiskMonths: this.asNumber(raw['low_risk_months']),
      marketingBudgetChangePct: this.asNumber(raw['marketing_budget_change_pct']),
      dynamicPricingRolloutPct: this.asNumber(raw['dynamic_pricing_rollout_pct']),
      newPackLaunches: this.asNumber(raw['new_pack_launches']),
      costControlPct: this.asNumber(raw['cost_control_pct']),
      salaryOptimizationPct: this.asNumber(raw['salary_optimization_pct']),
      marketDemandShockPct: this.asNumber(raw['market_demand_shock_pct']),
      supportAutomationPct: this.asNumber(raw['support_automation_pct']),
      focusTopMarketPct: this.asNumber(raw['focus_top_market_pct']),
      scenarioRank: this.asNumber(raw['scenario_rank'])
    };
  }

  private mapMonthPath(raw: Record<string, unknown>): FinanceScenarioMonthPath {
    return {
      scenarioName: this.asString(raw['scenario_name']),
      scenarioSlug: this.asString(raw['scenario_slug']),
      description: this.asString(raw['description']),
      month: this.asString(raw['month']),
      baselineIncome: this.asNumber(raw['baseline_income']),
      baselineExpenses: this.asNumber(raw['baseline_expenses']),
      baselineProfit: this.asNumber(raw['baseline_profit']),
      baselineMargin: this.asNumber(raw['baseline_margin']),
      projectedIncome: this.asNumber(raw['projected_income']),
      projectedExpenses: this.asNumber(raw['projected_expenses']),
      projectedProfit: this.asNumber(raw['projected_profit']),
      projectedMargin: this.asNumber(raw['projected_margin']),
      riskLevel: this.asString(raw['risk_level']),
      salaryExpense: this.asNumber(raw['salary_expense']),
      marketingExpense: this.asNumber(raw['marketing_expense']),
      infrastructureExpense: this.asNumber(raw['infrastructure_expense']),
      softwareExpense: this.asNumber(raw['software_expense']),
      otherContentExpense: this.asNumber(raw['other_content_expense']),
      otherSupportExpense: this.asNumber(raw['other_support_expense'])
    };
  }

  private mapDashboardSnapshot(raw: Record<string, unknown> | null | undefined): FinanceScenarioDashboardSnapshot | null {
    if (!raw) {
      return null;
    }

    return {
      month: this.asString(raw['month']),
      income: this.asNumber(raw['income']),
      expenses: this.asNumber(raw['expenses']),
      profit: this.asNumber(raw['profit']),
      margin: this.asNumber(raw['margin'])
    };
  }

  private asString(value: unknown): string {
    return typeof value === 'string' ? value : '';
  }

  private asNullableString(value: unknown): string | null {
    return typeof value === 'string' && value.length ? value : null;
  }

  private asNumber(value: unknown): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
  }

  private asNullableNumber(value: unknown): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }
}
