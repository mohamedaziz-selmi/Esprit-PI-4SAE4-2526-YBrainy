import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PackPricingSummary } from '../models/pack-pricing.model';

interface PackPricingAssetSummary {
  generated_at?: string | null;
  assumption?: string | null;
  scraper_inputs?: {
    courses_file?: string | null;
    skill_trends_file?: string | null;
  } | null;
  model?: {
    algorithm?: string | null;
    random_seed?: number | null;
    user_count?: number | null;
    discount_grid_pct?: number[] | null;
    training_rows?: number | null;
    test_rows?: number | null;
    positive_rate?: number | null;
    accuracy?: number | null;
    roc_auc?: number | null;
  } | null;
  portfolio_summary?: {
    current_expected_revenue_total?: number | null;
    recommended_expected_revenue_total?: number | null;
    revenue_uplift_pct?: number | null;
    packs_to_increase_price?: number | null;
    packs_to_decrease_price?: number | null;
    packs_to_hold_price?: number | null;
  } | null;
  top_pricing_recommendations?: Array<{
    pack_id?: number | null;
    title?: string | null;
    category_id?: number | null;
    category_name?: string | null;
    level?: string | null;
    primary_skill?: string | null;
    original_price?: number | null;
    current_sale_price?: number | null;
    current_discount_pct?: number | null;
    recommended_sale_price?: number | null;
    recommended_discount_pct?: number | null;
    discount_range_min_pct?: number | null;
    discount_range_max_pct?: number | null;
    recommended_band?: string | null;
    price_action?: string | null;
    baseline_conversion_rate?: number | null;
    recommended_conversion_rate?: number | null;
    baseline_expected_revenue?: number | null;
    recommended_expected_revenue?: number | null;
    revenue_lift_pct?: number | null;
    conversion_lift_pct?: number | null;
    pricing_confidence?: number | null;
    market_demand_score?: number | null;
    market_avg_roi?: number | null;
    market_course_count?: number | null;
    scenario_count?: number | null;
    pricing_rank?: number | null;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class PackPricingService {
  private readonly apiUrl = `${environment.apiUrl}/admin/packs/pricing`;
  private readonly fallbackSummaryUrl = 'assets/dynamic-pricing/dynamic_pricing_summary.json';

  constructor(private http: HttpClient) { }

  getSummary(limit = 10): Observable<PackPricingSummary> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<PackPricingSummary>(`${this.apiUrl}/summary`, { params }).pipe(
      catchError(() => this.getAssetSummary(limit))
    );
  }

  private getAssetSummary(limit: number): Observable<PackPricingSummary> {
    return this.http.get<PackPricingAssetSummary>(this.fallbackSummaryUrl).pipe(
      map((summary) => this.mapAssetSummary(summary, limit))
    );
  }

  private mapAssetSummary(summary: PackPricingAssetSummary, limit: number): PackPricingSummary {
    const topPricingRecommendations = (summary.top_pricing_recommendations ?? []).slice(0, limit).map((item) => ({
      packId: item.pack_id ?? null,
      title: item.title ?? null,
      categoryId: item.category_id ?? null,
      categoryName: item.category_name ?? null,
      level: item.level ?? null,
      primarySkill: item.primary_skill ?? null,
      originalPrice: item.original_price ?? null,
      currentSalePrice: item.current_sale_price ?? null,
      currentDiscountPct: item.current_discount_pct ?? null,
      recommendedSalePrice: item.recommended_sale_price ?? null,
      recommendedDiscountPct: item.recommended_discount_pct ?? null,
      discountRangeMinPct: item.discount_range_min_pct ?? null,
      discountRangeMaxPct: item.discount_range_max_pct ?? null,
      recommendedBand: item.recommended_band ?? null,
      priceAction: item.price_action ?? null,
      baselineConversionRate: item.baseline_conversion_rate ?? null,
      recommendedConversionRate: item.recommended_conversion_rate ?? null,
      baselineExpectedRevenue: item.baseline_expected_revenue ?? null,
      recommendedExpectedRevenue: item.recommended_expected_revenue ?? null,
      revenueLiftPct: item.revenue_lift_pct ?? null,
      conversionLiftPct: item.conversion_lift_pct ?? null,
      pricingConfidence: item.pricing_confidence ?? null,
      marketDemandScore: item.market_demand_score ?? null,
      marketAvgRoi: item.market_avg_roi ?? null,
      marketCourseCount: item.market_course_count ?? null,
      scenarioCount: item.scenario_count ?? null,
      pricingRank: item.pricing_rank ?? null
    }));

    return {
      sourceFile: this.fallbackSummaryUrl,
      generatedAt: summary.generated_at ?? new Date().toISOString(),
      assumption: summary.assumption ?? null,
      coursesFile: summary.scraper_inputs?.courses_file ?? null,
      skillTrendsFile: summary.scraper_inputs?.skill_trends_file ?? null,
      modelMetrics: summary.model ? {
        algorithm: summary.model.algorithm ?? null,
        randomSeed: summary.model.random_seed ?? null,
        userCount: summary.model.user_count ?? null,
        discountGridPct: summary.model.discount_grid_pct ?? [],
        trainingRows: summary.model.training_rows ?? null,
        testRows: summary.model.test_rows ?? null,
        positiveRate: summary.model.positive_rate ?? null,
        accuracy: summary.model.accuracy ?? null,
        rocAuc: summary.model.roc_auc ?? null
      } : null,
      portfolioSummary: summary.portfolio_summary ? {
        currentExpectedRevenueTotal: summary.portfolio_summary.current_expected_revenue_total ?? null,
        recommendedExpectedRevenueTotal: summary.portfolio_summary.recommended_expected_revenue_total ?? null,
        revenueUpliftPct: summary.portfolio_summary.revenue_uplift_pct ?? null,
        packsToIncreasePrice: summary.portfolio_summary.packs_to_increase_price ?? null,
        packsToDecreasePrice: summary.portfolio_summary.packs_to_decrease_price ?? null,
        packsToHoldPrice: summary.portfolio_summary.packs_to_hold_price ?? null
      } : null,
      champion: topPricingRecommendations[0] ?? null,
      topPricingRecommendations
    };
  }
}
