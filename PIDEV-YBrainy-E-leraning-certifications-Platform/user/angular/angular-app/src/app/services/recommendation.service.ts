import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RecommendationSummary } from '../models/recommendation.model';

interface RecommendationAssetSummary {
  forecast_context?: {
    margin_pct?: number | null;
    margin_trend_pct?: number | null;
    top_revenue_source?: string | null;
    watch_category?: string | null;
    watch_growth_pct?: number | null;
    forecast_horizon?: {
      from?: string | null;
      to?: string | null;
      months_ahead?: number | null;
      income_total?: number | null;
      expenses_total?: number | null;
      profit_total?: number | null;
    } | null;
  } | null;
  executive_metrics?: Array<{
    metric?: string | null;
    value?: string | null;
    notes?: string | null;
    status?: string | null;
  }>;
  top_recommendations?: Array<{
    recommendation_rank?: number | null;
    title?: string | null;
    platform?: string | null;
    skill_category?: string | null;
    hybrid_final_score?: number | null;
    confidence_level?: string | null;
    impact_band?: string | null;
    recommendation_reason?: string | null;
    url?: string | null;
    month?: string | null;
    margin_before?: number | null;
    margin_after?: number | null;
    profit_before?: number | null;
    profit_after?: number | null;
    profit_low_before?: number | null;
    profit_low_after?: number | null;
    action_key?: string | null;
    target_metric?: string | null;
    priority?: string | null;
    how?: string | null;
    expected_outcome?: string | null;
    success_signal?: string | null;
  }>;
  improvement_actions?: Array<{
    priority?: string | null;
    action?: string | null;
    rationale?: string | null;
    target_metric?: string | null;
  }>;
  user_playbook?: Array<{
    step?: number | null;
    window?: string | null;
    priority?: string | null;
    action?: string | null;
    why?: string | null;
    how?: string | null;
    expected_impact?: string | null;
    success_signal?: string | null;
    target_metric?: string | null;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private readonly apiUrl = `${environment.apiUrl}/recommendations`;
  private readonly fallbackSummaryUrl = 'assets/forecasting-recommendations/financial_recommendations_summary.json';

  constructor(private http: HttpClient) { }

  getSummary(limit = 10): Observable<RecommendationSummary> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<RecommendationSummary>(`${this.apiUrl}/summary`, { params }).pipe(
      catchError(() => this.getAssetSummary(limit))
    );
  }

  private getAssetSummary(limit: number): Observable<RecommendationSummary> {
    return this.http.get<RecommendationAssetSummary>(this.fallbackSummaryUrl).pipe(
      map((summary) => this.mapAssetSummary(summary, limit))
    );
  }

  private mapAssetSummary(summary: RecommendationAssetSummary, limit: number): RecommendationSummary {
    return {
      sourceFile: this.fallbackSummaryUrl,
      generatedAt: new Date().toISOString(),
      forecastContext: summary.forecast_context ? {
        marginPct: summary.forecast_context.margin_pct ?? null,
        marginTrendPct: summary.forecast_context.margin_trend_pct ?? null,
        topRevenueSource: summary.forecast_context.top_revenue_source ?? null,
        watchCategory: summary.forecast_context.watch_category ?? null,
        watchGrowthPct: summary.forecast_context.watch_growth_pct ?? null,
        forecastHorizon: summary.forecast_context.forecast_horizon ? {
          from: summary.forecast_context.forecast_horizon.from ?? null,
          to: summary.forecast_context.forecast_horizon.to ?? null,
          monthsAhead: summary.forecast_context.forecast_horizon.months_ahead ?? null,
          incomeTotal: summary.forecast_context.forecast_horizon.income_total ?? null,
          expensesTotal: summary.forecast_context.forecast_horizon.expenses_total ?? null,
          profitTotal: summary.forecast_context.forecast_horizon.profit_total ?? null
        } : null
      } : null,
      executiveMetrics: (summary.executive_metrics ?? []).map((metric) => ({
        metric: metric.metric ?? null,
        value: metric.value ?? null,
        notes: metric.notes ?? null,
        status: metric.status ?? null
      })),
      topRecommendations: (summary.top_recommendations ?? []).slice(0, limit).map((item) => ({
        recommendationRank: item.recommendation_rank ?? null,
        title: item.title ?? null,
        platform: item.platform ?? null,
        skillCategory: item.skill_category ?? null,
        hybridFinalScore: item.hybrid_final_score ?? null,
        confidenceLevel: item.confidence_level ?? null,
        impactBand: item.impact_band ?? null,
        recommendationReason: item.recommendation_reason ?? null,
        url: item.url ?? null,
        month: item.month ?? null,
        marginBefore: item.margin_before ?? null,
        marginAfter: item.margin_after ?? null,
        profitBefore: item.profit_before ?? null,
        profitAfter: item.profit_after ?? null,
        profitLowBefore: item.profit_low_before ?? null,
        profitLowAfter: item.profit_low_after ?? null,
        actionKey: item.action_key ?? null,
        targetMetric: item.target_metric ?? null,
        priority: item.priority ?? null,
        how: item.how ?? null,
        expectedOutcome: item.expected_outcome ?? null,
        successSignal: item.success_signal ?? null
      })),
      improvementActions: (summary.improvement_actions ?? []).map((action) => ({
        priority: action.priority ?? null,
        action: action.action ?? null,
        rationale: action.rationale ?? null,
        targetMetric: action.target_metric ?? null
      })),
      userPlaybook: (summary.user_playbook ?? []).map((step) => ({
        step: step.step ?? null,
        window: step.window ?? null,
        priority: step.priority ?? null,
        action: step.action ?? null,
        why: step.why ?? null,
        how: step.how ?? null,
        expectedImpact: step.expected_impact ?? null,
        successSignal: step.success_signal ?? null,
        targetMetric: step.target_metric ?? null
      }))
    };
  }
}
