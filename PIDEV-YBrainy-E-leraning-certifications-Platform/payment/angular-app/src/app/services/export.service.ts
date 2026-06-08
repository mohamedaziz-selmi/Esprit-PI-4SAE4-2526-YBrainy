import { Injectable } from '@angular/core';
import * as XLSX from 'xlsx';
import { jsPDF } from 'jspdf';
import 'jspdf-autotable';

@Injectable({
    providedIn: 'root'
})
export class ExportService {

    constructor() { }

    /**
     * Export data to Excel (.xlsx)
     * @param data Array of objects to export
     * @param fileName Name of the file (without extension)
     */
    exportToExcel(data: any[], fileName: string): void {
        const worksheet: XLSX.WorkSheet = XLSX.utils.json_to_sheet(data);
        const workbook: XLSX.WorkBook = {
            Sheets: { 'data': worksheet },
            SheetNames: ['data']
        };
        const excelBuffer: any = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
        this.saveAsExcelFile(excelBuffer, fileName);
    }

    /**
     * Export data to PDF using jspdf and jspdf-autotable
     * @param headers Table headers
     * @param data Table body data (array of arrays)
     * @param fileName Name of the file
     * @param title Title to display at the top of the PDF
     */
    exportToPDF(headers: string[], data: any[][], fileName: string, title: string): void {
        const doc = new jsPDF();

        // Add title
        doc.setFontSize(18);
        doc.text(title, 14, 22);
        doc.setFontSize(11);
        doc.setTextColor(100);

        // Add generation date
        const date = new Date().toLocaleString();
        doc.text(`Generated on: ${date}`, 14, 30);

        // Generate table
        (doc as any).autoTable({
            head: [headers],
            body: data,
            startY: 35,
            theme: 'striped',
            headStyles: { fillColor: [102, 126, 234] }, // Matches our theme color
            margin: { top: 35 }
        });

        doc.save(`${fileName}.pdf`);
    }

    private saveAsExcelFile(buffer: any, fileName: string): void {
        const data: Blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8' });
        const url = window.URL.createObjectURL(data);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${fileName}_export_${new Date().getTime()}.xlsx`;
        link.click();
        window.URL.revokeObjectURL(url);
    }
}
