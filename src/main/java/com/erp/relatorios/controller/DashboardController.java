package com.erp.relatorios.controller;

import com.erp.catalogo.model.Produto;
import com.erp.relatorios.model.ProdutoMaisVendidoDTO;
import com.erp.relatorios.model.VendasPorDiaDTO;
import com.erp.relatorios.service.RelatorioService;
import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.bar.BarChartDataSet;
import org.primefaces.model.charts.bar.BarChartModel;
import org.primefaces.model.charts.bar.BarChartOptions;
import org.primefaces.model.charts.optionconfig.legend.Legend;
import org.primefaces.model.charts.pie.PieChartDataSet;
import org.primefaces.model.charts.pie.PieChartModel;
import org.primefaces.model.charts.pie.PieChartOptions;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named("dashboardController")
@ViewScoped
public class DashboardController implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(DashboardController.class.getName());
    private static final String COR_PRIMARY  = "rgba(108, 99, 255, 0.85)";
    private static final String COR_PRIMARY_BORDER = "rgba(108, 99, 255, 1)";
    private static final String COR_SUCCESS  = "rgba(34, 197, 94, 0.85)";
    private static final String COR_WARNING  = "rgba(245, 158, 11, 0.85)";
    private static final String COR_DANGER   = "rgba(239, 68, 68, 0.85)";
    private static final String COR_INFO     = "rgba(59, 130, 246, 0.85)";
    private static final List<String> PALETA_PIZZA = Arrays.asList("rgba(108,99,255,0.85)", "rgba(34,197,94,0.85)", "rgba(245,158,11,0.85)", "rgba(59,130,246,0.85)", "rgba(239,68,68,0.85)");
    private static final List<String> PALETA_PIZZA_BORDA = Arrays.asList("rgba(108,99,255,1)", "rgba(34,197,94,1)", "rgba(245,158,11,1)", "rgba(59,130,246,1)", "rgba(239,68,68,1)");

    @EJB
    private RelatorioService relatorioService;

    private BigDecimal totalVendasHoje;
    private Long contagemVendasHoje;
    private BigDecimal totalVendasMes;
    private Long contagemEstoqueBaixo;
    private String totalVendasHojeFormatado;
    private String totalVendasMesFormatado;
    private List<ProdutoMaisVendidoDTO> top5Produtos;
    private List<Produto> produtosEstoqueBaixo;
    private List<VendasPorDiaDTO> vendasSemana;
    private BarChartModel graficoVendasSemana;
    private BarChartModel graficoTop5Produtos;
    private PieChartModel graficoPizza;

    @PostConstruct
    public void init() {
        LOG.info("[DashboardController] Inicializando...");
        carregarKpis();
        carregarTop5Produtos();
        carregarVendasSemana();
        carregarAlertasEstoque();
        construirGraficoBarras();
        construirGraficoTop5();
        construirGraficoPizza();
    }

    private void carregarKpis() {
        try {
            NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
            totalVendasHoje = relatorioService.totalVendasHoje();
            contagemVendasHoje = relatorioService.contagemVendasHoje();
            totalVendasMes = relatorioService.totalVendasMes();
            contagemEstoqueBaixo = relatorioService.contagemEstoqueBaixo();
            totalVendasHojeFormatado = fmt.format(totalVendasHoje);
            totalVendasMesFormatado = fmt.format(totalVendasMes);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Erro ao carregar KPIs", e);
            totalVendasHoje = BigDecimal.ZERO;
            totalVendasMes = BigDecimal.ZERO;
            contagemVendasHoje = 0L;
            contagemEstoqueBaixo = 0L;
            totalVendasHojeFormatado = "R$ 0,00";
            totalVendasMesFormatado = "R$ 0,00";
        }
    }

    private void carregarTop5Produtos() {
        try {
            top5Produtos = relatorioService.top5ProdutosMes();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Erro ao carregar top-5", e);
            top5Produtos = new ArrayList<>();
        }
    }

    private void carregarVendasSemana() {
        try {
            vendasSemana = relatorioService.vendasUltimos7Dias();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Erro ao carregar vendas", e);
            vendasSemana = new ArrayList<>();
        }
    }

    private void carregarAlertasEstoque() {
        try {
            produtosEstoqueBaixo = relatorioService.produtosEstoqueBaixo();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Erro ao carregar alertas", e);
            produtosEstoqueBaixo = new ArrayList<>();
        }
    }

    private void construirGraficoBarras() {
        graficoVendasSemana = new BarChartModel();
        ChartData data = new ChartData();
        BarChartDataSet dataset = new BarChartDataSet();
        dataset.setLabel("Faturamento (R$)");

        List<Number> valores = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> cores = new ArrayList<>();
        List<String> borda = new ArrayList<>();

        for (VendasPorDiaDTO dto : vendasSemana) {
            labels.add(dto.getDiaSemana());
            valores.add(dto.getTotalFaturado());
            cores.add(COR_PRIMARY);
            borda.add(COR_PRIMARY_BORDER);
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(cores);
        dataset.setBorderColor(borda);
        dataset.setBorderWidth(2);
        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoVendasSemana.setData(data);

        BarChartOptions opts = new BarChartOptions();
        Legend legend = new Legend();
        legend.setDisplay(false);
        opts.setLegend(legend);
        graficoVendasSemana.setOptions(opts);
        graficoVendasSemana.setExtender("dashboardBarExtender");
    }

    private void construirGraficoTop5() {
        graficoTop5Produtos = new BarChartModel();
        ChartData data = new ChartData();
        BarChartDataSet dataset = new BarChartDataSet();
        dataset.setLabel("Unidades Vendidas");

        List<Number> valores = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> cores = new ArrayList<>();

        String[] coresPaleta = {COR_PRIMARY, COR_SUCCESS, COR_INFO, COR_WARNING, COR_DANGER};

        for (int i = 0; i < top5Produtos.size(); i++) {
            ProdutoMaisVendidoDTO dto = top5Produtos.get(i);
            labels.add(dto.getNomeAbreviado());
            valores.add(dto.getTotalVendido());
            cores.add(coresPaleta[i % coresPaleta.length]);
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(cores);
        dataset.setBorderWidth(0);
        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoTop5Produtos.setData(data);

        BarChartOptions opts = new BarChartOptions();
        Legend legend = new Legend();
        legend.setDisplay(false);
        opts.setLegend(legend);
        graficoTop5Produtos.setOptions(opts);
        graficoTop5Produtos.setExtender("dashboardTop5Extender");
    }

    private void construirGraficoPizza() {
        graficoPizza = new PieChartModel();
        ChartData data = new ChartData();
        PieChartDataSet dataset = new PieChartDataSet();

        List<Number> valores = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (ProdutoMaisVendidoDTO dto : top5Produtos) {
            labels.add(dto.getNomeAbreviado());
            Double r = dto.getReceitaGerada();
            valores.add(r != null ? r : Double.valueOf(0.0));
        }

        dataset.setData(valores);
        dataset.setBackgroundColor(PALETA_PIZZA);
        dataset.setBorderColor(PALETA_PIZZA_BORDA);
        dataset.setBorderWidth(Arrays.asList(2));   
        data.addChartDataSet(dataset);
        data.setLabels(labels);
        graficoPizza.setData(data);

        PieChartOptions opts = new PieChartOptions();
        Legend legend = new Legend();
        legend.setDisplay(true);
        legend.setPosition("bottom");
        opts.setLegend(legend);
        graficoPizza.setOptions(opts);
        graficoPizza.setExtender("dashboardPieExtender");
    }

    public void atualizar() {
        init();
        LOG.info("Dashboard atualizado.");
    }

    public String classeAlertaEstoque(Produto p) {
        if (p.getQuantidadeEstoque() == null) return "";
        return p.getQuantidadeEstoque() == 0 ? "row-critico" : "row-alerta";
    }

    public boolean isTemAlertaEstoque() {
        return contagemEstoqueBaixo != null && contagemEstoqueBaixo > 0;
    }

    public BigDecimal getTotalVendasHoje() { return totalVendasHoje; }
    public Long getContagemVendasHoje() { return contagemVendasHoje; }
    public BigDecimal getTotalVendasMes() { return totalVendasMes; }
    public Long getContagemEstoqueBaixo() { return contagemEstoqueBaixo; }
    public String getTotalVendasHojeFormatado() { return totalVendasHojeFormatado; }
    public String getTotalVendasMesFormatado() { return totalVendasMesFormatado; }
    public List<ProdutoMaisVendidoDTO> getTop5Produtos() { return top5Produtos; }
    public List<Produto> getProdutosEstoqueBaixo() { return produtosEstoqueBaixo; }
    public List<VendasPorDiaDTO> getVendasSemana() { return vendasSemana; }
    public BarChartModel getGraficoVendasSemana() { return graficoVendasSemana; }
    public BarChartModel getGraficoTop5Produtos() { return graficoTop5Produtos; }
    public PieChartModel getGraficoPizza() { return graficoPizza; }
}
